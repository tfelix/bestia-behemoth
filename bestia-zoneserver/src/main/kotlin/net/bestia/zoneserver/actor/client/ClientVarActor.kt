package net.bestia.zoneserver.actor.client

import net.bestia.messages.ui.ClientVarRequestMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.client.ClientVarService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * This actor manages the handling of shortcuts for saving them onto the server
 * as well as sending requested shortcuts back to the client.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class ClientVarActor(
        private val cvarService: ClientVarService
) : BaseClientMessageRouteActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(ClientVarRequestMessage::class.java, this::handleCvarMessage)
  }

  /**
   * Gets all current shortcuts and sends them back to the client.
   *
   * @param msg The request.
   */
  private fun handleCvarMessage(msg: ClientVarRequestMessage) {
    cvarService[msg.accountId, msg.key] = msg.data
  }

  /*
  private fun handleCvarReq(msg: ClientVarRequestMessage) {
    val accId = msg.accountId
    val key = msg.key

    if (!cvarService.isOwnerOfVar(accId, key)) {
      return
    }

    val cvar = cvarService.find(accId, key)
    val cvarMsg = ClientVarResponseMessage(accId, msg.uuid, cvar.data)
    sendClient.tell(cvarMsg, self)
  }
  */

  companion object {
    const val NAME = "clientvar"
  }
}