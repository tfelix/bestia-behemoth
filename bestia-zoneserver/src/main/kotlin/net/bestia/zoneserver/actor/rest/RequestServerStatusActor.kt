package net.bestia.zoneserver.actor.rest

import akka.actor.AbstractActor
import net.bestia.messages.account.ServerStatusMessage
import net.bestia.zoneserver.configuration.RuntimeConfigService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * Sends the server login status to the user.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class RequestServerStatusActor(
        private val config: RuntimeConfigService
) : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(ServerStatusMessage.Request::class.java) { x -> handleStatusRequest() }
            .build()
  }

  private fun handleStatusRequest() {

    val level = config.maintenanceMode
    val reply = ServerStatusMessage(level, "")

    // Reply the message.
    sender.tell(reply, self)
  }

  companion object {
    const val NAME = "RESTserverStatus"
  }
}
