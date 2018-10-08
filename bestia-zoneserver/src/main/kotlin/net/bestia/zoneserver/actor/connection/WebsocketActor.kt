package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.PongMessage
import net.bestia.messages.login.LoginAuthRequestMessage
import net.bestia.messages.login.LoginAuthResponseMessage
import net.bestia.messages.login.LoginResponse
import net.bestia.zoneserver.client.AuthenticationService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
@Scope("prototype")
class WebsocketActor(
    private val authService: AuthenticationService
) : AbstractActor() {

  private lateinit var clientSocketActor: ActorRef
  private val latencyProvider = LatencyProvider()

  @Suppress("LeakingThis")
  private val authenticated = receiveBuilder()
      .matchAny(this::processAnyMessage)
      .build()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ActorRef::class.java, this::onInit)
        .match(LoginAuthRequestMessage::class.java, this::onAuth)
        .matchAny { context.stop(self) }
        .build()
  }

  private fun onAuth(authRequestMsg: LoginAuthRequestMessage) {
    if (!::clientSocketActor.isInitialized) {
      LOG.error { "Received auth message before ref to client was initialized" }
      context.stop(self)
      return
    }

    val respMessage = if (authService.isUserAuthenticated(authRequestMsg.token)) {
      context.become(authenticated)
      LoginAuthResponseMessage(LoginResponse.SUCCESS)
    } else {
      LoginAuthResponseMessage(LoginResponse.DENIED)
    }

    clientSocketActor.tell(respMessage, self)
  }

  private fun onInit(outActor: ActorRef) {
    this.clientSocketActor = outActor
  }

  // TODO Send Latency to the messages who need it.
  private fun processAnyMessage(msg: Any) {
    when (msg) {
      INIT -> {
        println("Init")
        sender.tell(ACK, self)
      }
      COMPLETE -> {
        println("Stream complete")
      }
      is PongMessage -> handleLatencyPong(msg)
      else -> {
        println("Received: $msg")
        sender.tell(ACK, self)
      }
    }
    sender.tell(ACK, self)
  }

  private fun handleLatencyPong(msg: PongMessage) {
    val latency = System.currentTimeMillis() - msg.start
    latencyProvider.addLatency(latency.toInt())
  }

  companion object {
    const val ACK = "onAck"
    const val INIT = "onInit"
    const val COMPLETE = "onComplete"
  }
}
