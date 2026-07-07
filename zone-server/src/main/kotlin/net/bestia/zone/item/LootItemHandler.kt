package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.item.Loot
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs2.World
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
  private val world: World
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

    val playerPos = world.getOrThrow(activeEntityId, Position::class).toVec3L()

    // Claim the loot atomically: strip the Loot component so a concurrent pickup racing for the
    // same access sees no Loot component and aborts. This removes the item from the ECS first,
    // before it is ever granted, avoiding duplication.
    val claimed = world.modify(msg.targetEntityId) { id ->
      val loot = world.get(id, Loot::class) ?: return@modify null
      val lootPos = world.getOrThrow(id, Position::class).toVec3L()

      if (playerPos.distance(lootPos) > MAX_LOOT_RANGE) {
        return@modify null
      }

      world.remove<Loot>(id)
      ClaimedLoot(loot.itemId, loot.amount, loot.uniqueId, lootPos)
    }

    if (claimed == null) {
      LOG.debug { "Player ${msg.playerId} could not loot entity ${msg.targetEntityId} (missing, out of range, or already looted)" }
      return true
    }

    // Fully remove the now loot-less ground entity and tell nearby clients it's gone.
    world.destroy(msg.targetEntityId)
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
    inventoryItemFactory.addItemToMasterAndEntity(
      masterId = masterId,
      activeEntityId = activeEntityId,
      itemIdentifier = item.identifier,
      amount = claimed.amount,
      uniqueId = claimed.uniqueId
    )

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MAX_LOOT_RANGE = 1L
  }
}
