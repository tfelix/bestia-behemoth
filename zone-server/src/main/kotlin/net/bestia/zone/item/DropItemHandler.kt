package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.InMessageProcessor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import kotlin.random.Random

@Component
class DropItemHandler(
  private val itemRepository: ItemRepository,
  private val inventoryItemFactory: InventoryItemFactory,
  private val lootEntityFactory: LootEntityFactory,
  private val connectionInfoService: ConnectionInfoService,
  private val world: World
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
    val dropPos: Vec3L? = world.modify(activeEntityId) { id ->
      val inventory = world.get(id, Inventory::class)

      if (inventory == null) {
        LOG.warn { "Entity $activeEntityId had no Inventory component but tried to drop an item" }
        return@modify null
      }

      if (!inventory.hasItem(msg.itemId.toInt())) {
        LOG.warn { "Entity $activeEntityId owned no item ${msg.itemId}" }
        return@modify null
      }

      // 1. Persist the removal to the DB first - this is the durable, crash-safe commit.
      val dbRemoved = inventoryItemFactory.removeItem(masterId, item.identifier, msg.amount)

      if (!dbRemoved) {
        LOG.warn { "Could not remove $msg.amount of item ${item.identifier} from master $masterId in DB" }
        return@modify null
      }

      // 2. Mutate the ECS inventory and sync it back to the owner via the existing dirty pipeline.
      inventory.removeAmount(msg.itemId.toInt(), msg.amount)
      world.markChanged(id, Inventory::class)

      val pos = world.getOrThrow(id, Position::class).toVec3L()
      Vec3L(
        pos.x + Random.nextLong(-1, 2),
        pos.y + Random.nextLong(-1, 2),
        pos.z
      )
    }

    // 3. Spawn the ground item entity outside of the entity access block.
    if (dropPos != null) {
      lootEntityFactory.createLootEntity(itemId = item.id, amount = msg.amount, pos = dropPos)
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
