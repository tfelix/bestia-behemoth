package net.bestia.zone.ecs.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/**
 * Resolves [ObtainItemIntent]s: whichever entity has one attached (master or player bestia,
 * whichever is currently the active entity - see `ConnectionInfoService.getActiveEntityId`) gets
 * checked against its carry capacity and, if it fits, the item is added to its ECS [Inventory]
 * component immediately. Runs before [CarryCapacitySystem] (`@Order(61)`) so a same-tick grant is
 * already reflected in the [CarryCapacity] the owner is about to be sent (same pattern as
 * `GainExpSystem` (60) -> [CarryCapacitySystem] for level-ups).
 */
@Component
@Order(59)
class ObtainItemIntentSystem(
  private val itemRepository: ItemRepository,
  private val lootItemEntitySpawner: LootItemEntitySpawner,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val connectionInfoService: ConnectionInfoService,
) : System {

  private data class ClaimedLoot(
    val item: Item,
    val amount: Int,
    val uniqueId: Long,
  )

  override val reads: ComponentClassSet = setOf(
    ObtainItemIntent.LootItemIntent::class, ObtainItemIntent.CreateItemIntent::class,
    Position::class, Account::class, GroundItemStack::class, CarryCapacity::class,
    Inventory::class
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
      val lootPos = get(itemStackEntityId, Position::class)?.toVec3L()
      if (lootPos == null) {
        LOG.warn { "$itemStackEntityId had no Position component, can not calculate loot distance; destroying it" }
        destroy(itemStackEntityId)
        return@modify null
      }

      if (looterPosition.distance(lootPos) > MAX_LOOT_RANGE) {
        return@modify null
      }

      val item = itemRepository.findByIdOrNull(stack.itemId)
      if (item == null) {
        LOG.error { "Ground item $itemStackEntityId references unknown item ${stack.itemId}; destroying it" }
        destroy(itemStackEntityId)
        return@modify null
      }

      if (!canObtain(this, entityId, item.weight * stack.amount)) {
        return@modify null // over capacity (or no inventory at all) - leave the stack on the ground
      }

      // destroy() alone notifies clients: ZoneEngine broadcasts a vanish to whoever the stack's
      // EntityVisual was synced to.
      destroy(itemStackEntityId)

      ClaimedLoot(item, stack.amount, stack.uniqueId)
    }

    if (claimed == null) {
      LOG.debug { "Entity $entityId could not loot ${intent.sourceEntityItemStackId} (missing, out of range, over capacity, or already looted)" }
      return
    }

    grantItem(world, entityId, claimed.item, claimed.amount, claimed.uniqueId)
  }

  private fun tryCreateItem(world: World, entityId: EntityId, intent: ObtainItemIntent.CreateItemIntent) {
    val item = itemRepository.findByIdOrNull(intent.itemId)
    if (item == null) {
      LOG.warn { "CreateItemIntent for entity $entityId references unknown item ${intent.itemId}, ignoring" }
      return
    }

    if (canObtain(world, entityId, item.weight * intent.amount)) {
      grantItem(world, entityId, item, intent.amount)
      return
    }

    // Over capacity (or the entity has no inventory at all, e.g. a bestia not wired for one yet):
    // drop it on the ground at the entity's feet instead of losing it.
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
  private fun grantItem(world: World, entityId: EntityId, item: Item, amount: Int, uniqueId: Long = 0L) {
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
        maxDurability = if (item.stackable) 0 else item.maxDurability
      )
    )

    schedulePersist(world, entityId, item, amount, uniqueId)
  }

  private fun schedulePersist(world: World, entityId: EntityId, item: Item, amount: Int, uniqueId: Long) {
    val accountId = world.get(entityId, Account::class)?.accountId
    if (accountId == null) {
      LOG.warn { "Entity $entityId has no Account component, granted item ${item.id} will not be persisted" }
      return
    }

    asyncJobExecutor.submit {
      val masterId = try {
        connectionInfoService.getMasterId(accountId)
      } catch (e: Exception) {
        LOG.warn(e) { "Could not resolve master for account $accountId, granted item ${item.id} will not be persisted" }
        return@submit
      }

      inventoryService.grantToMaster(masterId, item, amount, uniqueId)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MAX_LOOT_RANGE = 1L
  }
}
