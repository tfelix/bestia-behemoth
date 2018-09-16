package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.login.AuthResponse
import net.bestia.messages.login.LoginAuthRequestMessage
import net.bestia.messages.login.LoginAuthResponseMessage
import net.bestia.zoneserver.client.AuthenticationService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
@Scope("prototype")
class WebsocketActor(
        private val authService: AuthenticationService
) : AbstractActor() {

  private val authenticated = receiveBuilder()
          .matchAny(this::processAnyMessage)
          .build()

  private lateinit var clientSocketActor: ActorRef

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
      LoginAuthResponseMessage(AuthResponse.LOGIN_OK)
    } else {
      LoginAuthResponseMessage(AuthResponse.LOGIN_WRONG)
    }

    clientSocketActor.tell(respMessage, self)
  }

  private fun onInit(outActor: ActorRef) {
    this.clientSocketActor = outActor
  }

  private fun processAnyMessage(msg: Any) {
    when (msg) {
      INIT -> {
        println("Init")
        sender.tell(ACK, self)
      }
      COMPLETE -> {
        println("Stream complete")
      }
      else -> {
        println("Received: $msg")
        sender.tell(ACK, self)
      }
    }
    sender.tell(ACK, self)
  }

  companion object {
    const val ACK = "onAck"
    const val INIT = "onInit"
    const val COMPLETE = "onComplete"
  }
}
