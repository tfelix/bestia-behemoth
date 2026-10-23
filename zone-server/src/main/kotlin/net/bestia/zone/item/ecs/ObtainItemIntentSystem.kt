package net.bestia.zone.item.ecs

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
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.PlayerBestiaId
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
  private val metrics: GameMetrics = GameMetrics(),
) : System {
  override val phase = Phase.ITEMS

  private data class ClaimedLoot(
    val item: ItemTemplateRegistry.Template,
    val amount: Int,
    val uniqueId: Long,
  )

  /** Whose container the item is saved to: the master's own when [playerBestiaId] is null. */
  private data class ItemOwner(
    val masterId: Long,
    val playerBestiaId: PlayerBestiaId?,
  )

  override val reads: ComponentClassSet = setOf(
    ObtainItemIntent.LootItemIntent::class, ObtainItemIntent.CreateItemIntent::class,
    Position::class, Account::class, Master::class, OwnedBestia::class, GroundItemStack::class, CarryCapacity::class,
    Inventory::class, GroundItemIntegrity::class, LootProtection::class
  )
  override val writes: ComponentClassSet = setOf(
    ObtainItemIntent.LootItemIntent::class, ObtainItemIntent.CreateItemIntent::class, Inventory::class
  )

  override fun update(world: World, deltaTime: Float) {
    // Two separate queries, not one query(ObtainItemIntent::class): components are stored keyed by
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
    val claimed = world.modify(intent.sourceEntityItemStackId) { itemStackEntityId ->
      val stack = get(itemStackEntityId, GroundItemStack::class)
        ?: return@modify null
      if (get(itemStackEntityId, GroundItemIntegrity::class)?.destroyed == true) {
        return@modify null
      }
      if (!mayLoot(this, entityId, itemStackEntityId)) {
        return@modify null
      }
      val lootPos = get(itemStackEntityId, Position::class)?.toVec3L()
      if (lootPos == null) {
        LOG.warn { "$itemStackEntityId had no Position component, can not calculate loot distance; destroying it" }
        groundStackRemoval.remove(this, itemStackEntityId)
        return@modify null
      }

      if (looterPosition.distance(lootPos) > MAX_LOOT_RANGE) {
        return@modify null
      }

      val item = itemTemplates.templateOf(stack.itemId)
      if (item == null) {
        LOG.error { "Ground item $itemStackEntityId references unknown item ${stack.itemId}; destroying it" }
        groundStackRemoval.remove(this, itemStackEntityId)
        return@modify null
      }

      if (!canObtain(this, entityId, item.weight * stack.amount)) {
        return@modify null // over capacity (or no inventory at all) - leave the stack on the ground
      }

      // destroy() alone notifies clients: ZoneEngine broadcasts a vanish to whoever the stack's
      // EntityVisual was synced to.
      groundStackRemoval.remove(this, itemStackEntityId)

      ClaimedLoot(item, stack.amount, stack.uniqueId)
    }

    if (claimed == null) {
      LOG.debug { "Entity $entityId could not loot ${intent.sourceEntityItemStackId} (missing, out of range, over capacity, or already looted)" }
      return
    }

    grantItem(world, entityId, claimed.item, claimed.amount, claimed.uniqueId)
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

    if (canObtain(world, entityId, item.weight * intent.amount)) {
      grantItem(world, entityId, item, intent.amount, roll = intent.roll, craftedByMasterId = intent.craftedByMasterId)
      return
    }

    // Over capacity (or the entity has no inventory at all):
    // drop it on the ground at the entity's feet instead of losing it. A ground stack carries no instance
    // state, so a forged copy dropped here loses its roll - the same gap loot drops have.
    val pos = world.get(entityId, Position::class)?.toVec3L()
    if (pos == null) {
      LOG.warn { "Entity $entityId can not carry item ${intent.itemId} and has no Position to drop it at; item lost" }
      return
    }

    lootItemEntitySpawner.spawnLootItem(
      world = world,
      itemId = item.id,
      amount = intent.amount,
      pos = pos,
    )
  }

  /**
   * True if [entityId] has an inventory at all and adding [itemWeight] would still fit its carry capacity.
   *
   * Weighed against the live [Inventory] rather than [CarryCapacity.current], for two reasons that both
   * bite. One pass of [update] can resolve a loot intent and a create intent on the same entity, and gating
   * both on the same tick-old `current` would let both through and overfill by a whole stack; [grantItem]
   * mutates the inventory synchronously, so reading it back here is self-consistent. And `CurMax` clamps
   * `current` to `max`, so an entity that somehow ends up over its limit under-reports its load and the gate
   * would let still more in. `Inventory.totalWeight` cannot lie about either.
   */
  private fun canObtain(world: World, entityId: EntityId, itemWeight: Int): Boolean {
    val inventory = world.get(entityId, Inventory::class) ?: return false
    val capacity = world.get(entityId, CarryCapacity::class) ?: return false

    return inventory.totalWeight + itemWeight <= capacity.max
  }

  /** Adds [item] to [entityId]'s live ECS inventory and schedules the durable DB write. */
  private fun grantItem(
    world: World,
    entityId: EntityId,
    item: ItemTemplateRegistry.Template,
    amount: Int,
    uniqueId: Long = 0L,
    roll: ItemRoll? = null,
    craftedByMasterId: Long? = null,
  ) {
    val inventory = world.get(entityId, Inventory::class)
    if (inventory == null) {
      LOG.warn { "Entity $entityId lost its Inventory component before the grant could be applied, item ${item.id} lost" }
      return
    }
    inventory.addItem(
      Inventory.Item(
        itemId = item.id,
        amount = amount,
        weight = item.weight,
        uniqueId = uniqueId,
        stackable = item.stackable,
        // Mirrors what InventoryService.grant is about to mint: a fresh instance starts unworn, and a
        // stackable item gets no instance and therefore no durability at all. A re-attached instance
        // (uniqueId != 0, an item picked back up off the ground) keeps whatever wear it had, which this
        // cannot see - the client gets the real number on the next full inventory send.
        durability = if (item.stackable) 0 else item.maxDurability,
        maxDurability = if (item.stackable) 0 else item.maxDurability,
        slots = roll?.sockets ?: 0,
        itemLevel = roll?.itemLevel ?: itemStats.itemLevelOf(item.id),
        rolledStats = roll?.stats.orEmpty()
      )
    )

    metrics.itemsGranted(amount)
    schedulePersist(world, entityId, item.id, amount, uniqueId, roll, craftedByMasterId)
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
    roll: ItemRoll?,
    craftedByMasterId: Long?
  ) {
    val owner = itemOwnerOf(world, entityId)
    if (owner == null) {
      LOG.warn { "Entity $entityId belongs to no master, granted item $itemId will not be persisted" }
      return
    }

    // The live inventory already holds the item, so a write lost here is an item gone after a restart.
    asyncJobExecutor.submit(key = owner.masterId) {
      try {
        retryingTransientFailures {
          inventoryService.grantToOwner(
            owner.masterId, owner.playerBestiaId, itemId, amount, uniqueId, roll, craftedByMasterId
          )
        }
      } catch (e: Exception) {
        LOG.error { "${amount}x item $itemId for $owner was not saved and is gone after a restart" }
        throw e
      }
    }
  }

  private fun itemOwnerOf(world: World, entityId: EntityId): ItemOwner? {
    world.get(entityId, Master::class)?.let { return ItemOwner(it.masterId, null) }
    world.get(entityId, OwnedBestia::class)?.let { return ItemOwner(it.masterId, it.playerBestiaId) }

    return null
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MAX_LOOT_RANGE = 1L
  }
}
