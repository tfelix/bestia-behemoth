package net.bestia.zoneserver.actor.inventory

import net.bestia.messages.inventory.InventoryListMessage
import net.bestia.messages.inventory.InventoryListRequestMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.InventoryService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * This actor will create a list of the currently owned inventory items and send
 * them to the client. The inventory is dependend on the bestia which is
 * currently active.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class InventoryRequestActor(
        private val inventoryService: InventoryService
) : BaseClientMessageRouteActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(InventoryListRequestMessage::class.java, this::onRequestInventory)
  }

  private fun onRequestInventory(msg: InventoryListRequestMessage) {
    val invMsg = InventoryListMessage(msg.accountId)
    val items = inventoryService.findPlayerItemsForAccount(msg.accountId)
    invMsg.playerItems = items

    sendClient.tell(invMsg, self)
  }

  companion object {
    const val NAME = "requestInventory"
  }
}
