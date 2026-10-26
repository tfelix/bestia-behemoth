package net.bestia.zone.crafting.discovery

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.KnownRecipeStore
import net.bestia.zone.crafting.Recipe
import net.bestia.zone.crafting.ecs.KnownRecipes
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import kotlin.math.abs
import kotlin.random.Random

/**
 * Tries a mix of materials against the blueprints of one crafting skill that the master does not know yet.
 *
 * The materials are always spent. The answer says how close the mix came, so a player can search with reason
 * rather than by luck: in reach (learned or slipped), just out of the skill's reach, or nothing with the axes the
 * mix is furthest off on. See "Discovery" in the design docs.
 *
 * Shaped like [net.bestia.zone.crafting.CraftingService.resolve]: decided and taken off the live inventory on the
 * tick, and only applied once the database has given the materials up too.
 */
@Service
class DiscoveryService(
  private val crafters: Crafters,
  private val blueprints: BlueprintPoints,
  private val materialAxes: MaterialAxes,
  private val itemTemplates: ItemTemplateRegistry,
  private val structures: PlayerStructureService,
  private val inventoryService: InventoryService,
  private val knownRecipeStore: KnownRecipeStore,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val outMessageProcessor: OutMessageProcessor,
  private val random: Random = Random.Default,
) {

  /** [materials] are player item id to amount, as the client sent them. */
  fun discover(
    world: World,
    crafterId: EntityId,
    masterEntityId: EntityId,
    skillId: Long,
    materials: List<Pair<Long, Int>>,
  ) {
    val accountId = world.get(crafterId, Account::class)?.accountId ?: return
    val crafter = crafters.of(world, crafterId, masterEntityId) ?: return refuse(accountId, OpError.CRAFT_NOT_POSSIBLE)
    val skillLevel = crafter.skills?.levelOf(skillId) ?: 0
    val inventory = world.get(crafterId, Inventory::class)
    if (skillLevel <= 0 || inventory == null) {
      return refuse(accountId, OpError.CRAFT_NOT_POSSIBLE)
    }

    val mix = mixOf(inventory, materials) ?: return refuse(accountId, OpError.CRAFT_MISSING_MATERIALS)

    val position = world.get(crafterId, Position::class)?.toVec3L()
    val candidates = blueprints.discoverable(skillId)
      .filterNot { crafter.knowsBlueprint(it.recipe) }
      .filter { atItsStation(world, position, it.recipe) }
    val result = judge(mix.point, candidates, skillLevel)

    mix.piles.forEach { inventory.take(it.held.playerItemId, it.amount) }

    val masterId = crafter.master.masterId
    asyncJobExecutor.submit(masterId) {
      val named = mix.piles.map { InventoryService.NamedPile(it.held.itemId, it.held.provenance, it.amount) }
      if (!inventoryService.takeNamedPiles(masterId, crafter.playerBestiaId, named)) {
        LOG.warn { "Master $masterId lost the materials of a discovery in the database; giving them back" }
        world.modify(crafterId) { id -> mix.piles.forEach { get(id, Inventory::class)?.addItem(it.givenBack()) } }
        return@submit refuse(accountId, OpError.CRAFT_MISSING_MATERIALS)
      }

      if (result.outcome == DiscoveryResultSMSG.Outcome.LEARNED) {
        knownRecipeStore.learn(masterId, result.recipeId)
        world.modify(masterEntityId) { id -> get(id, KnownRecipes::class)?.learn(result.recipeId) }
      }
      outMessageProcessor.sendToPlayer(accountId, result)
    }
  }

  /** What the mix finds among [candidates], the blueprints the master could still learn here. */
  internal fun judge(point: CraftVector, candidates: List<BlueprintPoints.Blueprint>, skillLevel: Int): DiscoveryResultSMSG {
    val nearest = candidates.minByOrNull { it.point.distanceTo(point) }
      ?: return DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.NOTHING)
    val distance = nearest.point.distanceTo(point)
    val radius = radiusOf(skillLevel)

    return when {
      distance <= radius -> {
        val learned = random.nextFloat() <= learnChance(skillLevel, levelOf(nearest.recipe))
        if (learned) {
          DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.LEARNED, recipeId = nearest.recipe.id)
        } else {
          DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.SLIPPED)
        }
      }

      distance <= radius * BEYOND_SKILL_FACTOR -> DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.BEYOND_SKILL)

      else -> DiscoveryResultSMSG(DiscoveryResultSMSG.Outcome.NOTHING, hints = hintsToward(nearest.point, point))
    }
  }

  /** The axes the mix is furthest off on, largest first, so the player knows which way to move. */
  private fun hintsToward(target: CraftVector, mix: CraftVector): List<DiscoveryResultSMSG.AxisHint> =
    CraftAxis.entries
      .map { axis -> axis to mix[axis] - target[axis] }
      .filter { (_, offBy) -> abs(offBy) >= HINT_THRESHOLD }
      .sortedByDescending { (_, offBy) -> abs(offBy) }
      .take(MAX_HINTS)
      .map { (axis, offBy) -> DiscoveryResultSMSG.AxisHint(axis, tooHigh = offBy > 0) }

  private fun levelOf(recipe: Recipe): Int =
    recipe.output?.let { itemTemplates.baseLevelOf(it.itemId) } ?: 1

  private fun atItsStation(world: World, position: Vec3L?, recipe: Recipe): Boolean {
    val station = recipe.station ?: return true

    return position != null && structures.stationNear(world, position, station) != null
  }

  /** Null when a named pile is not held in that amount, or is not a material at all. */
  private fun mixOf(inventory: Inventory, materials: List<Pair<Long, Int>>): Mix? {
    val requested = materials.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.sum() }
    if (requested.isEmpty() || requested.values.any { it <= 0 } || requested.values.sum() > MAX_UNITS) {
      return null
    }

    val piles = requested.map { (playerItemId, amount) ->
      val held = inventory.byPlayerItemId(playerItemId)?.takeIf { it.isStackable && it.amount >= amount } ?: return null
      Pile(held, amount)
    }
    val parts = piles.map { pile -> (materialAxes.of(pile.held.itemId) ?: return null) to pile.amount }

    return Mix(CraftVector.mixOf(parts), piles)
  }

  private fun refuse(accountId: Long, code: OpError) {
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private class Mix(val point: CraftVector, val piles: List<Pile>)

  private class Pile(val held: Inventory.Item, val amount: Int) {
    fun givenBack() = Inventory.Item(held.itemId, amount, held.weight, provenance = held.provenance)
  }

  companion object {
    /** How far a blueprint may lie from the mix and still be found. Grows with the skill level. */
    fun radiusOf(skillLevel: Int): Double = BASE_RADIUS + RADIUS_PER_LEVEL * skillLevel

    /**
     * `(skill / item level)²` from the design docs, with the skill counted as 10 per level, and never below 0.1%
     * so every blueprint stays discoverable in theory.
     */
    fun learnChance(skillLevel: Int, itemLevel: Int): Float {
      val ratio = SKILL_PER_LEVEL * skillLevel / itemLevel.coerceAtLeast(1).toFloat()

      return (ratio * ratio).coerceIn(MIN_LEARN_CHANCE, 1f)
    }

    private const val BASE_RADIUS = 0.15
    private const val RADIUS_PER_LEVEL = 0.03
    private const val BEYOND_SKILL_FACTOR = 1.5
    private const val SKILL_PER_LEVEL = 10f
    private const val MIN_LEARN_CHANCE = 0.001f

    /** An axis closer than this is not worth naming. */
    private const val HINT_THRESHOLD = 0.1
    private const val MAX_HINTS = 2

    /** A mix is a handful of materials, not a warehouse. */
    private const val MAX_UNITS = 100

    private val LOG = KotlinLogging.logger { }
  }
}
