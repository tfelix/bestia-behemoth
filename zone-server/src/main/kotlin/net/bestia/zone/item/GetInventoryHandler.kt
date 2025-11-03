package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Handles the client request to get the inventory of an entity.
 */
@Component
class GetInventoryHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val zoneServer: ZoneServer
) : InMessageProcessor.IncomingMessageHandler<GetInventoryCMSG> {
  override val handles: KClass<GetInventoryCMSG> = GetInventoryCMSG::class

  override fun handle(msg: GetInventoryCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val response = zoneServer.withEntityReadLock(msg.entityId) {
      val inventory = it.get(Inventory::class)
        ?: return@withEntityReadLock null

      InventorySMSG(
        entityId = msg.entityId,
        items = inventory.items.map { invItem ->
          InventorySMSG.PlayerItem(
            itemId = invItem.itemId,
            amount = invItem.amount,
            playerItemId = invItem.uniqueId
          )
        }
      )
    }

    if (response == null) {
      LOG.warn { "No inventory items found: entity ${msg.entityId} did not have Inventory component" }
      return true
    }

    outMessageProcessor.sendToPlayer(msg.playerId, response)

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
