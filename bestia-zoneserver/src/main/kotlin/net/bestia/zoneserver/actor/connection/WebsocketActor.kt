package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.http.javadsl.model.ws.TextMessage
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import net.bestia.messages.MessageId
import net.bestia.messages.client.ClientEnvelope
import net.bestia.messages.client.LatencyInfo
import net.bestia.messages.client.PongMessage
import net.bestia.messages.login.LoginAuthRequestMessage
import net.bestia.messages.login.LoginAuthResponseMessage
import net.bestia.messages.login.LoginResponse
import net.bestia.zoneserver.client.AuthenticationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.io.IOException

private val LOG = KotlinLogging.logger { }

@Component
@Scope("prototype")
class WebsocketActor(
    private val authService: AuthenticationService,
    @Qualifier("socketMapper")
    private val mapper: ObjectMapper,
    private val clientMessageIngress: ActorRef
) : AbstractActor() {

  enum class ConnectionState {
    WAITING_FOR_INIT,
    WAITING_FOR_AUTH,
    CONNECTED
  }

  private var connectionState = ConnectionState.WAITING_FOR_INIT
  private var connectedAccountId = 0L
  private lateinit var clientSocketActor: ActorRef

  private val latencyProvider = LatencyProvider()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ClientEnvelope::class.java, this::onClientEnvelope)
        .match(TextMessage::class.java, this::onClientTextMessage)
        .match(ActorRef::class.java, this::onInit)
        .matchEquals(INIT) { sender.tell(ACK, self) }
        .matchEquals(COMPLETE) {
          sender.tell(ACK, self)
          LOG.debug { "Completed client connection" }
        }
        .build()
  }

  private fun onClientEnvelope(msg: ClientEnvelope) {
    if (connectionState != ConnectionState.CONNECTED) {
      LOG.warn { "Not in connected state. Cant send to client yet" }
      unhandled(msg)
      return
    }

    val content = msg.content

    if (content is LatencyInfo) {
      content.latency = latencyProvider.getLatency()
    }

    clientSocketActor.tell(serialize(content), sender)
  }

  private fun onInit(outActor: ActorRef) {
    sender.tell(ACK, self)

    if (connectionState != ConnectionState.WAITING_FOR_INIT) {
      LOG.error { "Received ActorRef in state $connectionState. Must be in state ${ConnectionState.WAITING_FOR_INIT}" }
      context.stop(self)
      return
    }

    clientSocketActor = outActor
    connectionState = ConnectionState.WAITING_FOR_AUTH
    clientSocketActor.tell(CLIENT_ON_CONNECTED_MSG, self)
  }

  private fun onClientTextMessage(textMessage: TextMessage) {
    sender.tell(ACK, self)

    val msg = deserialize(textMessage.strictText) ?: return
    LOG.trace { "Client send: $msg" }

    when (connectionState) {
      ConnectionState.CONNECTED -> onClientMessageAuthenticated(msg)
      ConnectionState.WAITING_FOR_AUTH -> onClientMessageUnauthenticated(msg)
      else -> {
        LOG.warn { "Client send too early. Terminating." }
        unhandled(msg)
        context.stop(self)
      }
    }
  }

  private fun onLatencyPong(msg: PongMessage) {
    val latency = System.currentTimeMillis() - msg.start
    latencyProvider.addLatency(latency.toInt())
  }

  private fun onClientMessageAuthenticated(msg: Any) {
    when (msg) {
      is PongMessage -> onLatencyPong(msg)
      else -> {
        val envelopedClientMessage = ClientEnvelope(
            accountId = connectedAccountId,
            content = msg
        )
        clientMessageIngress.tell(envelopedClientMessage, self)
      }
    }
  }

  private fun onClientMessageUnauthenticated(msg: Any) {
    if (msg !is LoginAuthRequestMessage) {
      LOG.warn { "Client send no required LoginAuthRequestMessage. Terminating connection." }
      unhandled(msg)
      context.stop(self)
      return
    }

    val isClientAuthenticated = authService.isUserAuthenticated(msg.token)
    val respMessage = if (isClientAuthenticated) {
      connectionState = ConnectionState.CONNECTED
      LoginAuthResponseMessage(LoginResponse.SUCCESS)
    } else {
      context.stop(self)
      LoginAuthResponseMessage(LoginResponse.DENIED)
    }

    sendToClientSocket(respMessage)
  }
  
  private fun sendToClientSocket(msg: Any) {
    clientSocketActor.tell(serialize(msg), self)
  }

  private fun deserialize(msg: String): MessageId? {
    return try {
      mapper.readValue(msg, MessageId::class.java)
    } catch (e: IOException) {
      LOG.warn { "Could not deserialize message: $msg" }
      null
    }
  }

  private fun serialize(msg: Any): String? {
    return try {
      mapper.writeValueAsString(msg)
    } catch (e: IOException) {
      LOG.warn(e) { "Could not serialize message: $msg" }
      null
    }
  }

  companion object {
    const val ACK = "onAck"
    const val INIT = "onInit"
    const val COMPLETE = "onComplete"
    const val CLIENT_ON_CONNECTED_MSG = "connected"
  }
}
