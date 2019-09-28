package net.bestia.zoneserver.actor.client

import net.bestia.messages.client.ClientEnvelope
import net.bestia.messages.ui.ClientVarRequestMessage
import net.bestia.messages.ui.ClientVarResponseMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.account.ClientVarService
import net.bestia.zoneserver.actor.Actor

/**
 * This actor manages the handling of shortcuts for saving them onto the server
 * as well as sending requested shortcuts back to the client.
 *
 * @author Thomas Felix
 */
@Actor
class ClientVarActor(
    private val cvarService: ClientVarService
) : DynamicMessageRoutingActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(ClientVarRequestMessage::class.java, this::handleCvarRequest)
  }

  /**
   * Gets all current shortcuts and sends them back to the client.
   *
   * @param msg The request.
   */
  private fun handleCvarRequest(msg: ClientVarRequestMessage) {
    val accId = msg.accountId
    val key = msg.key

    if (!cvarService.isOwnerOfVar(accId, key)) {
      return
    }

    val cvar = cvarService.find(accId, key)
    val cvarMsg = ClientVarResponseMessage(msg.uuid, cvar.getDataAsString())
    sendClient.tell(ClientEnvelope(accId, cvarMsg), self)
  }

  companion object {
    const val NAME = "clientvar"
  }
}