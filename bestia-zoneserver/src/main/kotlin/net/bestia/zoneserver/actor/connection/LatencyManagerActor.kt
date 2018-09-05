package net.bestia.zoneserver.actor.connection

import net.bestia.messages.client.PongMessage
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.client.LatencyService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
class LatencyManagerActor(
        private val latencyService: LatencyService
) : BaseClientMessageRouteActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.match(PongMessage::class.java, this::onPongMessage)
  }

  private fun onPongMessage(msg: PongMessage) {
    latencyService.addLatency(msg.accountId, msg.start, System.currentTimeMillis())
  }

  companion object {
    const val NAME = "latency"
  }
}