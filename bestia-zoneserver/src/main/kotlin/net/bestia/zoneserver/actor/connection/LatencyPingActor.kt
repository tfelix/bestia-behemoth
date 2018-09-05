package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import net.bestia.messages.client.PingMessage
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.client.LatencyService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * Periodically pings the client to receive the pong request.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class LatencyPingActor(
        private val latencyService: LatencyService,
        private val accountId: Long,
        private val clientConnection: ActorRef
) : BaseClientMessageRouteActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.matchEquals(LATENCY_REQUEST_MSG, { onLatencyRequest() })
  }

  private val latencyTick = context.system.scheduler().schedule(
          Duration.create(2, TimeUnit.SECONDS),
          Duration.create(5, TimeUnit.SECONDS),
          self, LATENCY_REQUEST_MSG, context.dispatcher(), null)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()

            .build()
  }

  @Throws(Exception::class)
  override fun preStart() {
    val now = System.currentTimeMillis()
    latencyService.addLatency(accountId, now, now + 10)
  }

  @Throws(Exception::class)
  override fun postStop() {
    latencyTick.cancel()
    latencyService.delete(accountId)
  }

  /**
   * The client is send a latency request message.
   */
  private fun onLatencyRequest() {

    // Check how many latency requests we have missed.
    val lastReply = latencyService.getLastClientReply(accountId)
    val dLastReply = System.currentTimeMillis() - lastReply

    if (lastReply > 0 && dLastReply > CLIENT_TIMEOUT_MS) {
      // Connection seems to have dropped. Signal the server that the
      // client has disconnected and terminate.
      context.parent().tell(PoisonPill.getInstance(), self)
    } else {
      val ping = PingMessage(accountId)
      clientConnection.tell(ping, self)
    }
  }

  companion object {
    const val NAME = "ping"
    private const val LATENCY_REQUEST_MSG = "latency"
    private const val CLIENT_TIMEOUT_MS = 30 * 1000
  }
}
