package net.bestia.zoneserver.actor.client

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.messages.ui.ClientVarRequest
import net.bestia.messages.ui.ClientVarResponse
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.account.ClientVarService
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

/**
 * This actor manages the handling of shortcuts for saving them onto the server
 * as well as sending requested shortcuts back to the client.
 *
 * @author Thomas Felix
 */
@Actor
class ClientVarActor(
    private val cvarService: ClientVarService,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(ClientVarRequest::class.java, this::handleCvarRequest)
  }

  /**
   * Gets all current shortcuts and sends them back to the client.
   *
   * @param msg The request.
   */
  private fun handleCvarRequest(msg: ClientVarRequest) {
    LOG.trace { "Received: $msg" }

    val accId = msg.accountId
    val key = msg.key

    if (!cvarService.isOwnerOfVar(accId, key)) {
      return
    }

    if (!msg.valueToSet.isNullOrEmpty()) {
      cvarService.setCvar(accId, key, msg.valueToSet!!)
    }

    val cvar = cvarService.findCvar(accId, key)
    val cvarMsg = ClientVarResponse(msg.key, cvar.getDataAsString())
    sendClientActor.tell(ClientEnvelope(accId, cvarMsg), self)
  }

  companion object {
    const val NAME = "clientvar"
  }
}