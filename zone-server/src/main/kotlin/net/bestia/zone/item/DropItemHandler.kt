package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.message.InMessageProcessor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class DropItemHandler(
  private val itemRepository: ItemRepository,
  private val inventoryService: InventoryService,
  private val lootItemEntityFactory: LootItemEntityFactory,
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<DropItemCMSG> {
  override val handles = DropItemCMSG::class

  override fun handle(msg: DropItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    if (msg.amount <= 0) {
      LOG.warn { "Invalid drop amount ${msg.amount} from player ${msg.playerId}" }
      return true
    }

    val item = itemRepository.findByIdOrNull(msg.itemId)

    if (item == null) {
      LOG.warn { "Item ${msg.itemId} was not found in the database" }
      return true
    }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    val masterId = connectionInfoService.getMasterId(msg.playerId)

    // Access the entity, verify preconditions from ECS info and persist the removal to the
    // database immediately (critical item transaction - must not risk duplication).
    val dropped: Dropped? = world.modify(activeEntityId) { id ->
      val inventory = get(id, Inventory::class)

      if (inventory == null) {
        LOG.warn { "Entity $activeEntityId had no Inventory component but tried to drop an item" }
        return@modify null
      }

      if (!inventory.hasItem(msg.itemId.toInt())) {
        LOG.warn { "Entity $activeEntityId owned no item ${msg.itemId}" }
        return@modify null
      }

      // 1. Persist the removal to the DB first - this is the durable, crash-safe commit. The DB is
      //    the source of truth for which physical item leaves the inventory: it prefers a unique
      //    instance and hands back its uniqueId so the dropped item keeps its identity.
      val removed = inventoryService.removeOneFromMaster(masterId, item.id, msg.amount)

      if (removed == null) {
        LOG.warn { "Could not remove ${msg.amount} of item ${item.identifier} from master $masterId in DB" }
        return@modify null
      }

      // 2. Mirror the removal in the ECS inventory; it marks itself dirty and syncs back to the owner.
      val groundAmount = if (removed.uniqueId != 0L) {
        inventory.removeByUniqueId(removed.uniqueId)
        1
      } else {
        inventory.removeAmount(msg.itemId.toInt(), msg.amount)
        msg.amount
      }

      val pos = getOrThrow(id, Position::class).toVec3L()
      Dropped(
        uniqueId = removed.uniqueId,
        amount = groundAmount,
        pos = Vec3L(
          pos.x + Random.nextLong(-1, 2),
          pos.y + Random.nextLong(-1, 2),
          pos.z
        )
      )
    }

    // 3. Spawn the ground item entity outside of the entity access block.
    if (dropped != null) {
      lootItemEntityFactory.createLootEntity(
        world,
        itemId = item.id,
        amount = dropped.amount,
        pos = dropped.pos,
        uniqueId = dropped.uniqueId
      )
    }

    return true
  }

  private data class Dropped(
    val uniqueId: Long,
    val amount: Int,
    val pos: Vec3L,
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
