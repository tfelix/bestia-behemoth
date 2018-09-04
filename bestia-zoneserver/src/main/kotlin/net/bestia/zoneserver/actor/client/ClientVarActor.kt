package net.bestia.zoneserver.actor.client

import mu.KotlinLogging
import net.bestia.messages.ui.ClientVarMessage
import net.bestia.messages.ui.ClientVarRequestMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.client.ClientVarService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

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

  init {
    this.requestMessages(ClientVarRequestMessage::class.java, { this.handleCvarMessage(it) })
  }

  /**
   * Gets all current shortcuts and sends them back to the client.
   *
   * @param msg The request.
   */
  private fun handleCvarMessage(msg: ClientVarRequestMessage) {
    when (msg.mode) {
      ClientVarRequestMessage.Mode.DEL -> handleCvarDelete(msg)
      ClientVarRequestMessage.Mode.SET -> handleCvarSet(msg)
      ClientVarRequestMessage.Mode.REQ -> handleCvarReq(msg)
      else -> LOG.warn { "Unknown mode in cvar msg: ${msg.mode}." }
    }
  }

  private fun handleCvarSet(msg: ClientVarRequestMessage) {
    cvarService[msg.accountId, msg.key] = msg.data
  }

  private fun handleCvarDelete(msg: ClientVarRequestMessage) {
    if (cvarService.isOwnerOfVar(msg.accountId, msg.key)) {
      cvarService.delete(msg.accountId, msg.key)
    }
  }

  private fun handleCvarReq(msg: ClientVarRequestMessage) {
    val accId = msg.accountId
    val key = msg.key

    if (!cvarService.isOwnerOfVar(accId, key)) {
      return
    }

    val cvar = cvarService.find(accId, key)
    val cvarMsg = ClientVarMessage(accId, msg.uuid, cvar.data)
    sendClient.tell(cvarMsg, self)
  }

  companion object {
    const val NAME = "clientvar"
  }
}