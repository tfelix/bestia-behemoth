package net.bestia.zone.item.ecs

import net.bestia.zone.battle.ecs.exp.GainExpSystem
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.GameMetrics
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemRoll
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.PlayerItemIds
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.persistence.retryingTransientFailures

/**
 * Resolves [ObtainItemIntent]s: whichever entity has one attached (master or player bestia,
 * whichever is currently the active entity - see `ConnectionInfoService.getActiveEntityId`) gets
 * checked against its carry capacity and, if it fits, the item is added to its ECS [Inventory]
 * component immediately. Runs before [CarryCapacitySystem] so a same-tick grant is already reflected
 * in the [CarryCapacity] the owner is about to be sent, as a level-up from `GainExpSystem` is.
 */
@Component
class ObtainItemIntentSystem(
  private val itemTemplates: ItemTemplateRegistry,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val groundStackRemoval: GroundStackRemoval,
  private val itemStats: ItemStatCatalog,
  private val worldView: WorldView,
  private val pickupRules: List<GroundPickupRule> = emptyList(),
  private val metrics: GameMetrics = GameMetrics(),
) : System {
  override val phase = Phase.ITEMS

  // A pickup rule may cost honor or set a status, which experience gains touch too.
  override val after = setOf(GainExpSystem::class)

  private data class ClaimedLoot(
    val item: ItemTemplateRegistry.Template,
    val amount: Int,
    val weight: Int,
    val uniqueId: Long,
    val provenance: ItemProvenance?,
    val roll: ItemRoll?,
  )

  /** Whose container the item is saved to: the master's own when [playerBestiaId] is null. */
  override val reads: ComponentClassSet = setOf(
    ObtainItemIntent.LootItemIntent::class, ObtainItemIntent.CreateItemIntent::class,
    ObtainItemIntent.CreateItemsIntent::class, Position::class, Account::class, Master::class, OwnedBestia::class, GroundItemStack::class, CarryCapacity::class,
    Inventory::class, GroundItemIntegrity::class, LootProtection::class
  ) + pickupRules.flatMap { it.reads }
  override val writes: ComponentClassSet = setOf(
    ObtainItemIntent.LootItemIntent::class, ObtainItemIntent.CreateItemIntent::class,
    ObtainItemIntent.CreateItemsIntent::class, Inventory::class, Animation::class
  ) + pickupRules.flatMap { it.writes }

  override fun update(world: World, deltaTime: Float) {
    // Separate queries, not one query(ObtainItemIntent::class): components are stored keyed by
    // their concrete runtime type (see World.add), so a sealed base type is never itself a usable
    // query/store key - only its concrete subclasses are.
    world.query(ObtainItemIntent.LootItemIntent::class).each { entityId ->
      tryLootItem(world, entityId, get<ObtainItemIntent.LootItemIntent>())
      world.remove(entityId, ObtainItemIntent.LootItemIntent::class)
    }

    world.query(ObtainItemIntent.CreateItemIntent::class).each { entityId ->
      tryCreateItem(world, entityId, get<ObtainItemIntent.CreateItemIntent>())
      world.remove(entityId, ObtainItemIntent.CreateItemIntent::class)
    }

    world.query(ObtainItemIntent.CreateItemsIntent::class).each { entityId ->
      get<ObtainItemIntent.CreateItemsIntent>().grants.forEach { tryCreateItem(world, entityId, it) }
      world.remove(entityId, ObtainItemIntent.CreateItemsIntent::class)
    }
  }

  private fun tryLootItem(world: World, entityId: EntityId, intent: ObtainItemIntent.LootItemIntent) {
    val looterPosition = world.get(entityId, Position::class)?.toVec3L()
    if (looterPosition == null) {
      LOG.warn { "Looter entity $entityId has no Position component, aborting loot" }
      return
    }

    // Claim the loot atomically inside a single lock-held scope: only destroy the ground stack
    // once we know it can actually be granted (in range, still there, fits the looter's carry
    // capacity), so a rejected loot leaves the stack on the ground instead of losing it.
    var refusal = "the stack is gone"
    val claimed = world.modify(intent.sourceEntityItemStackId) { itemStackEntityId ->
      val stack = get(itemStackEntityId, GroundItemStack::class)
        ?: return@modify null
      if (get(itemStackEntityId, GroundItemIntegrity::class)?.destroyed == true) {
        return@modify null
      }
      if (!mayLoot(this, entityId, itemStackEntityId)) {
        refusal = "it is another account's protected kill loot"
        return@modify null
      }
      val lootPos = get(itemStackEntityId, Position::class)?.toVec3L()
      if (lootPos == null) {
        LOG.warn { "$itemStackEntityId had no Position component, can not calculate loot distance; destroying it" }
        groundStackRemoval.remove(this, itemStackEntityId)
        return@modify null
      }

      val distance = looterPosition.distance(lootPos)
      if (distance > MAX_LOOT_RANGE) {
        refusal = "it is $distance tiles away, reach is $MAX_LOOT_RANGE"
        return@modify null
      }

      val item = itemTemplates.templateOf(stack.itemId)
      if (item == null) {
        LOG.error { "Ground item $itemStackEntityId references unknown item ${stack.itemId}; destroying it" }
        groundStackRemoval.remove(this, itemStackEntityId)
        return@modify null
      }

      val weight = stack.heldWeight ?: item.weight
      if (!CarryCheck.fits(this, entityId, weight * stack.amount)) {
        refusal = "it does not fit the looter's carry capacity"
        return@modify null // leave the stack on the ground
      }
      if (pickupRules.any { !it.mayPickUp(this, entityId, itemStackEntityId) }) {
        refusal = "a rule of its own keeps it on the ground"
        return@modify null
      }
      pickupRules.forEach { it.onPickUp(this, entityId, itemStackEntityId) }

      // destroy() alone notifies clients: ZoneEngine broadcasts a vanish to whoever the stack's
      // EntityVisual was synced to.
      groundStackRemoval.remove(this, itemStackEntityId)

      ClaimedLoot(item, stack.amount, weight, stack.uniqueId, stack.provenance, stack.roll)
    }

    if (claimed == null) {
      LOG.debug { "Entity $entityId could not loot ${intent.sourceEntityItemStackId}: $refusal" }
      return
    }

    grantItem(world, entityId, claimed.item, claimed.amount, claimed.uniqueId, claimed.roll, claimed.provenance, claimed.weight)
    // Only a loot that worked: a refused one leaves the stack lying there, which says enough.
    world.get(entityId, Animation::class)?.startPickUp()
  }

  /** Protected kill loot is its killer's alone; a master and its bestias share one account. */
  private fun mayLoot(world: World, looterId: EntityId, itemStackEntityId: EntityId): Boolean {
    val protection = world.get(itemStackEntityId, LootProtection::class) ?: return true

    return world.get(looterId, Account::class)?.accountId == protection.ownerAccountId
  }

  private fun tryCreateItem(world: World, entityId: EntityId, intent: ObtainItemIntent.CreateItemIntent) {
    val item = itemTemplates.templateOf(intent.itemId)
    if (item == null) {
      LOG.warn { "CreateItemIntent for entity $entityId references unknown item ${intent.itemId}, ignoring" }
      return
    }

    if (CarryCheck.fits(world, entityId, item.weight * intent.amount)) {
      grantItem(world, entityId, item, intent.amount, roll = intent.roll, provenance = intent.provenance, freshId = intent.freshId)
      return
    }

    // Over capacity (or the entity has no inventory at all):
    // drop it on the ground at the entity's feet instead of losing it. The roll and the maker go along, so the
    // copy made on pickup is the one that was forged.
    val pos = world.get(entityId, Position::class)?.toVec3L()
    if (pos == null) {
      LOG.warn { "Entity $entityId can not carry item ${intent.itemId} and has no Position to drop it at; item lost" }
      return
    }

    // Deferred: a new ground item is a structural change, and its components are none of this system's.
    world.defer {
      lootItemEntitySpawner.spawnLootItem(
        world = world,
        itemId = item.id,
        amount = intent.amount,
        pos = pos,
        provenance = intent.provenance,
        roll = intent.roll,
      )
    }
  }

  /** Adds [item] to [entityId]'s live ECS inventory and schedules the durable DB write. */
  private fun grantItem(
    world: World,
    entityId: EntityId,
    item: ItemTemplateRegistry.Template,
    amount: Int,
    uniqueId: Long = 0L,
    roll: ItemRoll? = null,
    provenance: ItemProvenance? = null,
    weight: Int = item.weight,
    freshId: Long = 0L,
  ) {
    val inventory = world.get(entityId, Inventory::class)
    if (inventory == null) {
      LOG.warn { "Entity $entityId lost its Inventory component before the grant could be applied, item ${item.id} lost" }
      return
    }
    // Fresh copies of gear are minted here rather than by the database, one per unit, so each shows up as a copy
    // the player can already name.
    val freshInstanceIds = when {
      uniqueId != 0L || item.stackable -> emptyList()
      freshId != 0L && amount == 1 -> listOf(freshId)
      else -> List(amount) { PlayerItemIds.next() }
    }
    val heldIds = freshInstanceIds.ifEmpty { listOf(uniqueId) }

    heldIds.forEach { heldId ->
      inventory.addItem(
        Inventory.Item(
          itemId = item.id,
          amount = if (freshInstanceIds.isEmpty()) amount else 1,
          weight = weight,
          uniqueId = heldId,
          stackable = item.stackable,
          // Mirrors what InventoryService.grant is about to mint: a fresh instance starts unworn, and a
          // stackable item gets no instance and therefore no durability at all. A re-attached instance
          // (uniqueId != 0, an item picked back up off the ground) keeps whatever wear it had, which this
          // cannot see - the persist job mirrors it once the row is read.
          durability = if (item.stackable) 0 else item.maxDurability,
          maxDurability = if (item.stackable) 0 else item.maxDurability,
          slots = roll?.sockets ?: 0,
          itemLevel = roll?.itemLevel ?: itemStats.itemLevelOf(item.id),
          rolledStats = roll?.stats.orEmpty(),
          provenance = provenance
        )
      )
    }

    metrics.itemsGranted(amount)
    schedulePersist(world, entityId, item.id, amount, uniqueId, freshInstanceIds, roll, provenance)
  }

  /**
   * The owner is read off the entity on the tick, which still knows it after a logout. Keyed by master so the
   * grant is ordered against the other writes of that master and its bestias.
   */
  private fun schedulePersist(
    world: World,
    entityId: EntityId,
    itemId: Long,
    amount: Int,
    uniqueId: Long,
    freshInstanceIds: List<Long>,
    roll: ItemRoll?,
    provenance: ItemProvenance?
  ) {
    val owner = ItemOwner.of(world, entityId)
    if (owner == null) {
      LOG.warn { "Entity $entityId belongs to no master, granted item $itemId will not be persisted" }
      return
    }

    // The live inventory already holds the item, so a write lost here is an item gone after a restart.
    asyncJobExecutor.submit(key = owner.masterId) {
      try {
        val reattached = retryingTransientFailures {
          inventoryService.grantToOwner(
            owner.masterId, owner.playerBestiaId, itemId, amount, uniqueId, roll, provenance, freshInstanceIds
          )
        }
        // Otherwise a half-burnt torch picked back up would look, and fold back into its pile, as a fresh one.
        reattached?.let { instance ->
          worldView.modify(entityId) { id ->
            get(id, Inventory::class)?.mirrorWear(instance.id, instance.durability, instance.maxDurability)
          }
        }
      } catch (e: Exception) {
        LOG.error { "${amount}x item $itemId for $owner was not saved and is gone after a restart" }
        throw e
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MAX_LOOT_RANGE = 1L
  }
}
