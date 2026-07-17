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
import net.bestia.zone.item.inventory.InventoryItemFactory
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Resolves [ObtainItemIntent]s: whichever entity has one attached (master or player bestia,
 * whichever is currently the active entity - see `ConnectionInfoService.getActiveEntityId`) gets
 * checked against its carry capacity and, if it fits, the item is added to its ECS [Inventory]
 * component immediately. Runs before [CarryCapacitySystem] (`@Order(61)`) so a same-tick inventory
 * change is already reflected in [CarryCapacity] this tick (same pattern as `ExpSystem` (60) ->
 * [CarryCapacitySystem] for level-ups).
 *
 */
@Component
@Order(59)
class ObtainItemIntentSystem(
  private val lootItemEntityFactory: LootItemEntityFactory,
  private val itemWeightRegistry: ItemWeightRegistry,
  private val inventoryItemFactory: InventoryItemFactory,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val connectionInfoService: ConnectionInfoService,
) : System {

  private data class ClaimedLoot(
    val amount: Int,
    val playerItemId: Long,
  )

  override val reads: ComponentClassSet = setOf(
    ObtainItemIntent.LootItemIntent::class, ObtainItemIntent.CreateItemIntent::class,
    Position::class, Account::class, ItemVisual::class, CarryCapacity::class
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
      val itemVisual = get(itemStackEntityId, ItemVisual::class)
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

      val itemWeight = itemWeightRegistry.getWeight(itemVisual.itemId)
      if (itemWeight == null) {
        LOG.error { "Ground item $itemStackEntityId references unknown item ${itemVisual.itemId}; destroying it" }
        destroy(itemStackEntityId)
        return@modify null
      }

      if (!canObtain(this, entityId, itemWeight * itemVisual.amount)) {
        return@modify null // over capacity (or no inventory at all) - leave the stack on the ground
      }

      // destroy() alone notifies clients: ZoneEngine broadcasts a vanish to whoever ItemVisual
      // was synced to.
      destroy(itemStackEntityId)

      ClaimedLoot(item, itemVisual.amount, itemVisual.playerItemId)
    }

    if (claimed == null) {
      LOG.debug { "Entity $entityId could not loot ${intent.sourceEntityItemStackId} (missing, out of range, over capacity, or already looted)" }
      return
    }

    grantItem(world, entityId, claimed.item, claimed.amount, claimed.playerItemId)
  }

  private fun tryCreateItem(world: World, entityId: EntityId, intent: ObtainItemIntent.CreateItemIntent) {
    val itemWeight = itemWeightRegistry.getWeight(intent.itemId)
    if (itemWeight == null) {
      LOG.warn { "CreateItemIntent for entity $entityId references unknown item ${intent.itemId}, ignoring" }
      return
    }

    if (canObtain(world, entityId, itemWeight * intent.amount)) {
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

    // FIXME DO NOT RE_CREATE THE LOOT ITEM ENITIY THIS WILL RESPAWN. PLEASE JUST DO NOT DESTROY THE EXISTING ENTITY.
    lootItemEntityFactory.createLootEntity(
      world = world,
      itemId = item.id,
      amount = intent.amount,
      pos = pos,
    )
  }

  /** True if [entityId] has an inventory at all and adding [itemWeight] would still fit its carry capacity. */
  private fun canObtain(world: World, entityId: EntityId, itemWeight: Int): Boolean {
    if (!world.has(entityId, Inventory::class)) return false
    val capacity = world.get(entityId, CarryCapacity::class) ?: return false

    return capacity.current + itemWeight <= capacity.max
  }

  /** Adds [item] to [entityId]'s live ECS inventory and schedules the durable DB write. */
  private fun grantItem(world: World, entityId: EntityId, itemId: Long, amount: Int, playerItemId: Long? = null) {
    val inventory = world.get(entityId, Inventory::class)
    if (inventory == null) {
      LOG.warn { "Entity $entityId lost its Inventory component before the grant could be applied, item $itemId lost" }
      return
    }
    inventory.addItem(Inventory.Item(itemId = itemId, amount = amount))

    schedulePersist(world, entityId, item, amount)
  }

  private fun schedulePersist(world: World, entityId: EntityId, itemId: Long, amount: Int) {
    val accountId = world.get(entityId, Account::class)?.accountId
    if (accountId == null) {
      LOG.warn { "Entity $entityId has no Account component, granted item $itemId will not be persisted" }
      return
    }

    val masterId = try {
      connectionInfoService.getMasterId(accountId)
    } catch (e: Exception) {
      LOG.warn(e) { "Could not resolve master for account $accountId, granted item $itemId will not be persisted" }
      return
    }

    asyncJobExecutor.submit(key = masterId) {
      // TODO look into how this would work with an item inventory
      inventoryItemFactory.addItemToMaster(masterId, item.identifier, amount)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MAX_LOOT_RANGE = 1L
  }
}
