package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.Loot
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.entity.VanishEntitySMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class LootItemHandler(
  private val itemRepository: ItemRepository,
  private val inventoryItemFactory: InventoryItemFactory,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
  private val zoneServer: ZoneServer
) : InMessageProcessor.IncomingMessageHandler<LootItemCMSG> {
  override val handles = LootItemCMSG::class

  private data class ClaimedLoot(
    val itemId: Long,
    val amount: Int,
    val uniqueId: Long,
    val pos: Vec3L
  )

  override fun handle(msg: LootItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    val masterId = connectionInfoService.getMasterId(msg.playerId)

    val playerPos = zoneServer.withEntityReadLockOrThrow(activeEntityId) {
      it.getOrThrow(Position::class).toVec3L()
    }

    // Claim the loot atomically: strip the Loot component under the target's write lock so a
    // concurrent pickup racing for the same lock sees no Loot component and aborts. This is
    // what removes the item from the ECS first, before it is ever granted, avoiding duplication.
    val claimed = zoneServer.withEntityWriteLock(msg.targetEntityId) { entity ->
      val loot = entity.get(Loot::class) ?: return@withEntityWriteLock null
      val lootPos = entity.getOrThrow(Position::class).toVec3L()

      if (playerPos.distance(lootPos) > MAX_LOOT_RANGE) {
        return@withEntityWriteLock null
      }

      entity.remove(Loot::class)
      ClaimedLoot(loot.itemId, loot.amount, loot.uniqueId, lootPos)
    }

    if (claimed == null) {
      LOG.debug { "Player ${msg.playerId} could not loot entity ${msg.targetEntityId} (missing, out of range, or already looted)" }
      return true
    }

    // Fully remove the now loot-less ground entity and tell nearby clients it's gone.
    zoneServer.removeEntity(msg.targetEntityId)
    outMessageProcessor.sendToAllPlayersInRange(
      claimed.pos,
      VanishEntitySMSG(entityId = msg.targetEntityId, kind = VanishEntitySMSG.VanishKind.GONE)
    )

    val item = itemRepository.findByIdOrNull(claimed.itemId)

    if (item == null) {
      LOG.error { "Looted item ${claimed.itemId} (entity ${msg.targetEntityId}) not found in DB, item lost for player ${msg.playerId}" }
      return true
    }

    // Persist to the DB first (durable), then sync the ECS inventory + mark dirty so the
    // owner receives the updated InventoryComponentSMSG via the existing dirty pipeline.
    inventoryItemFactory.addItemToMaster(masterId, item.identifier, claimed.amount)

    zoneServer.withEntityWriteLock(activeEntityId) { entity ->
      val inventory = entity.get(Inventory::class)

      if (inventory == null) {
        LOG.warn { "Entity $activeEntityId has no Inventory component, cannot sync looted item ${item.identifier}" }
        return@withEntityWriteLock
      }

      inventory.addItem(Inventory.Item(itemId = item.id.toInt(), amount = claimed.amount, uniqueId = claimed.uniqueId))
      entity.add(IsDirty)
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MAX_LOOT_RANGE = 1L
  }
}
