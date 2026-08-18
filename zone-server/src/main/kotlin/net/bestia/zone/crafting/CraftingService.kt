package net.bestia.zone.crafting

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.crafting.Crafting
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.instance.ItemInstance
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Service
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Everything a craft is: what a crafter can make here, starting one, and what happens when it resolves.
 *
 * ### Only a master crafts
 *
 * Every skill in the Craftsman and Blacksmith trees is a *master* skill, and the durable side of a craft goes
 * through the master's own [net.bestia.zone.item.container.ItemContainer]. A bestia standing at a forge is
 * refused rather than silently crafting out of its owner's bag.
 *
 * ### The ECS inventory is the authority inside the tick; the database catches up
 *
 * [resolve] runs on the tick thread from [net.bestia.zone.ecs.crafting.CraftingSystem], where a transaction
 * would stall the whole simulation. So it checks and mutates the live [Inventory] component synchronously and
 * hands the durable write to [AsyncJobExecutor] keyed on the master id - exactly the shape
 * `ObtainItemIntentSystem.schedulePersist` uses, and the per-key ordering is what keeps two crafts a second
 * apart from landing out of order.
 *
 * The crafted item itself is granted through [ObtainItemIntent.CreateItemIntent] rather than written here, so
 * it goes through the one path that already checks carry capacity and persists the grant.
 */
@Service
class CraftingService(
  private val recipes: RecipeRegistry,
  private val bonuses: MasterCraftBonusService,
  private val inventoryService: InventoryService,
  private val structures: PlayerStructureService,
  private val outMessageProcessor: OutMessageProcessor,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /**
   * Answers "what can I make here" for the skill that was just activated.
   *
   * Lists a recipe the crafter cannot currently afford, deliberately: a recipe you have no materials for is
   * what tells you what to go and gather, and hiding it would make the tree look empty. What it does *not* list
   * is a recipe whose station is missing or whose level is too high - those are not near-misses, they are
   * somewhere else entirely.
   */
  fun offerRecipes(world: World, casterId: EntityId, skillId: Long) {
    val accountId = world.get(casterId, Account::class)?.accountId ?: return
    val known = world.get(casterId, KnownSkills::class)
    val inventory = world.get(casterId, Inventory::class)
    val position = world.get(casterId, Position::class)?.toVec3L()

    var stationFound: StaticEntityKind? = null

    val offered = recipes.forSkill(skillId)
      .filter { known?.knowsSkill(skillId, it.requiredSkillLevel) == true }
      .filter { recipe ->
        val station = recipe.station ?: return@filter true
        if (position == null) return@filter false

        val standing = structures.stationNear(world, position, station) != null
        if (standing) stationFound = station

        standing
      }
      .map { recipe -> describe(recipe, known, inventory) }

    outMessageProcessor.sendToPlayer(
      accountId,
      CraftableRecipesSMSG(skillId = skillId, station = stationFound, recipes = offered)
    )
  }

  /**
   * Validates a craft and starts its timer. Nothing is spent yet - see [Crafting].
   *
   * @return the refusal to send back, or null when the craft is now under way
   */
  fun start(world: World, entityId: EntityId, recipeId: Long, targetUniqueId: Long): OpError? {
    val recipe = recipes.of(recipeId) ?: return OpError.CRAFT_NOT_POSSIBLE

    if (world.get(entityId, Crafting::class) != null) {
      return OpError.CRAFT_ALREADY_IN_PROGRESS
    }

    if (world.get(entityId, Master::class) == null) {
      LOG.debug { "Entity $entityId is not a master and cannot craft" }
      return OpError.CRAFT_NOT_POSSIBLE
    }

    val known = world.get(entityId, KnownSkills::class)
    if (known?.knowsSkill(recipe.requiredSkillId, recipe.requiredSkillLevel) != true) {
      return OpError.CRAFT_NOT_POSSIBLE
    }

    recipe.station?.let { station ->
      val position = world.get(entityId, Position::class)?.toVec3L() ?: return OpError.CRAFT_NOT_POSSIBLE
      if (structures.stationNear(world, position, station) == null) return OpError.CRAFT_NOT_POSSIBLE
    }

    val inventory = world.get(entityId, Inventory::class) ?: return OpError.CRAFT_NOT_POSSIBLE

    if (recipe.needsTarget && targetFor(recipe, inventory, targetUniqueId, known) == null) {
      return OpError.CRAFT_NOT_POSSIBLE
    }

    // Checked at the start as well as at the end, so a player is told now rather than after waiting out a
    // craft that was never going to resolve.
    if (!holdsInputs(recipe, inventory)) {
      return OpError.CRAFT_MISSING_MATERIALS
    }

    world.add(
      entityId,
      Crafting(
        recipeId = recipe.id,
        targetUniqueId = if (recipe.needsTarget) targetUniqueId else 0L,
        totalSeconds = bonuses.craftSeconds(known, recipe)
      )
    )

    return null
  }

  /**
   * Spends the inputs, rolls, and applies whatever the recipe does.
   *
   * Everything is re-checked here rather than trusted from [start]: a craft takes seconds, and in that time the
   * materials can be dropped, the target traded away or the station knocked down. The inputs are spent *before*
   * the roll and are never returned, which is the entire risk in a craft and the reason the success tables in
   * the design docs are worth anything.
   */
  fun resolve(world: World, entityId: EntityId, crafting: Crafting) {
    val recipe = recipes.of(crafting.recipeId) ?: return
    val accountId = world.get(entityId, Account::class)?.accountId
    val masterId = world.get(entityId, Master::class)?.masterId ?: return
    val inventory = world.get(entityId, Inventory::class) ?: return
    val known = world.get(entityId, KnownSkills::class)

    val target = if (recipe.needsTarget) {
      targetFor(recipe, inventory, crafting.targetUniqueId, known) ?: return refuse(accountId, OpError.CRAFT_NOT_POSSIBLE)
    } else {
      null
    }

    if (!consumeInputs(recipe, inventory, masterId)) {
      return refuse(accountId, OpError.CRAFT_MISSING_MATERIALS)
    }

    val chance = bonuses.successChance(known, recipe)
    if (Random.nextFloat() > chance) {
      return fail(world, entityId, accountId, masterId, recipe, target, known)
    }

    when (recipe.effect) {
      RecipeEffect.PRODUCE -> {
        val output = recipe.output ?: return
        // Through the intent rather than written here, so the grant goes through the one path that checks carry
        // capacity and persists - see the class note.
        world.add(entityId, ObtainItemIntent.CreateItemIntent(itemId = output.itemId, amount = output.amount))
      }

      RecipeEffect.ADD_SLOT -> applyToTarget(inventory, masterId, target!!.copy(slots = target.slots + 1))
      RecipeEffect.UPGRADE -> applyToTarget(inventory, masterId, target!!.copy(upgradeLevel = target.upgradeLevel + 1))
      RecipeEffect.REPAIR -> applyToTarget(inventory, masterId, target!!.copy(durability = target.maxDurability))
    }

    LOG.debug { "Master $masterId completed ${recipe.identifier} (${(chance * 100).roundToInt()}% chance)" }
    accountId?.let { outMessageProcessor.sendToPlayer(it, OperationSuccessSMSG(OpSuccess.CRAFT_SUCCEEDED)) }
  }

  /**
   * A failed craft. The materials are already gone; the only extra consequence is a failed rune-slot cut, which
   * may take the item with it.
   */
  private fun fail(
    world: World,
    entityId: EntityId,
    accountId: Long?,
    masterId: Long,
    recipe: Recipe,
    target: TargetState?,
    known: KnownSkills?
  ) {
    if (recipe.effect == RecipeEffect.ADD_SLOT && target != null &&
      Random.nextFloat() <= bonuses.destroyChance(known)
    ) {
      world.get(entityId, Inventory::class)?.removeByUniqueId(target.uniqueId)
      asyncJobExecutor.submit(masterId) { inventoryService.destroyInstance(masterId, target.uniqueId) }

      LOG.debug { "A failed slot cut destroyed instance ${target.uniqueId} of master $masterId" }
      return refuse(accountId, OpError.CRAFT_ITEM_DESTROYED)
    }

    LOG.debug { "Master $masterId failed ${recipe.identifier}" }
    refuse(accountId, OpError.CRAFT_FAILED)
  }

  private fun refuse(accountId: Long?, code: OpError) {
    accountId?.let { outMessageProcessor.sendToPlayer(it, OperationErrorSMSG(code)) }
  }

  /**
   * Mirrors the new state onto the live inventory and schedules the durable write.
   *
   * The two halves cannot be one call: the ECS copy is what the client sees this tick, and the row is what
   * survives a restart. A write that only did the second would show the player nothing until they logged out.
   */
  private fun applyToTarget(inventory: Inventory, masterId: Long, updated: TargetState) {
    inventory.updateInstanceState(
      uniqueId = updated.uniqueId,
      durability = updated.durability,
      slots = updated.slots,
      upgradeLevel = updated.upgradeLevel
    )

    asyncJobExecutor.submit(masterId) {
      inventoryService.updateInstance(masterId, updated.uniqueId) { instance -> updated.writeOnto(instance) }
    }
  }

  /**
   * The held item a targeted recipe may work on, or null when it is not a legal target.
   *
   * Read off the live [Inventory] rather than the database, both because this runs on the tick thread and
   * because the ECS copy is the one the player is looking at. A worn item is not in scope: the inventory
   * component keeps it listed but flagged, and every effect here refuses it - you take gear off to work on it.
   */
  private fun targetFor(
    recipe: Recipe,
    inventory: Inventory,
    uniqueId: Long,
    known: KnownSkills?
  ): TargetState? {
    if (uniqueId == 0L) return null

    val held = inventory.getItems().firstOrNull { it.uniqueId == uniqueId } ?: return null
    if (held.equipped) return null

    val state = TargetState(
      uniqueId = uniqueId,
      durability = held.durability,
      maxDurability = held.maxDurability,
      slots = held.slots,
      upgradeLevel = held.upgradeLevel
    )

    return when (recipe.effect) {
      // Nothing to repair is a refusal rather than a no-op craft: it would take the materials for nothing.
      RecipeEffect.REPAIR -> state.takeIf { it.maxDurability > 0 && it.durability < it.maxDurability }

      RecipeEffect.ADD_SLOT -> state.takeIf { it.slots < bonuses.maxSlots(known) }

      RecipeEffect.UPGRADE -> state.takeIf { it.upgradeLevel < MAX_UPGRADE_LEVEL }

      RecipeEffect.PRODUCE -> null
    }
  }

  private fun holdsInputs(recipe: Recipe, inventory: Inventory): Boolean =
    required(recipe).all { (itemId, amount) -> heldAmount(inventory, itemId) >= amount }

  /** Takes the inputs off the live inventory and schedules the durable removal. False if any is short. */
  private fun consumeInputs(recipe: Recipe, inventory: Inventory, masterId: Long): Boolean {
    val required = required(recipe)
    if (required.any { (itemId, amount) -> heldAmount(inventory, itemId) < amount }) return false

    required.forEach { (itemId, amount) -> inventory.removeAmount(itemId.toInt(), amount) }

    asyncJobExecutor.submit(masterId) {
      inventoryService.consumeAll(masterId, required.map { (itemId, amount) -> itemId to amount })
    }

    return true
  }

  /** Summed per item, so a recipe naming a material twice is checked against the total. */
  private fun required(recipe: Recipe): Map<Long, Int> =
    recipe.inputs.groupBy({ it.itemId }, { it.amount }).mapValues { (_, amounts) -> amounts.sum() }

  /**
   * How many of [itemId] the crafter holds, counting only stackable piles.
   *
   * Instance slots are excluded on purpose: `InventoryService.consumeAll` can only take from stacks, so
   * counting a unique sword towards a recipe's iron would let a craft pass the check here and then throw when
   * the durable half could not honour it.
   */
  private fun heldAmount(inventory: Inventory, itemId: Long): Int =
    inventory.getItems().filter { it.itemId == itemId && it.isStackable }.sumOf { it.amount }

  private fun describe(
    recipe: Recipe,
    known: KnownSkills?,
    inventory: Inventory?
  ): CraftableRecipesSMSG.CraftableRecipe = CraftableRecipesSMSG.CraftableRecipe(
    recipeId = recipe.id,
    effect = recipe.effect,
    outputItemId = recipe.output?.itemId?.toInt() ?: 0,
    outputAmount = recipe.output?.amount ?: 0,
    inputs = recipe.inputs.map { input ->
      CraftableRecipesSMSG.Input(
        itemId = input.itemId.toInt(),
        amount = input.amount,
        held = inventory?.let { heldAmount(it, input.itemId) } ?: 0
      )
    },
    successPermille = (bonuses.successChance(known, recipe) * 1000).roundToInt(),
    craftMillis = (bonuses.craftSeconds(known, recipe) * 1000).roundToInt()
  )

  /**
   * The per-instance state a targeted craft reads and writes, snapshotted off the live inventory.
   *
   * A value rather than the [ItemInstance] itself, because the instance row cannot be touched from the tick
   * thread - so the new state is computed here and [writeOnto] applies it inside the async transaction.
   */
  private data class TargetState(
    val uniqueId: Long,
    val durability: Int,
    val maxDurability: Int,
    val slots: Int,
    val upgradeLevel: Int
  ) {
    fun writeOnto(instance: ItemInstance) {
      instance.durability = durability
      instance.slots = slots
      instance.upgradeLevel = upgradeLevel
    }
  }

  companion object {
    /**
     * How far an upgrade can be pushed.
     *
     * The docs give Upgrade Equipment no cap at all, and unbounded is not a design - every equip script scales
     * off the level, so a hundred successful upgrades would be a hundred times the stat. Ten is the number
     * every other per-level table in the tree tops out at.
     */
    const val MAX_UPGRADE_LEVEL = 10

    private val LOG = KotlinLogging.logger { }
  }
}
