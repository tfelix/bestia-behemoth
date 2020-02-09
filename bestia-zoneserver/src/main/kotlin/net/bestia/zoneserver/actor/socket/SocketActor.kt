package net.bestia.zoneserver.actor.socket

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.cluster.singleton.ClusterSingletonProxy
import akka.cluster.singleton.ClusterSingletonProxySettings
import akka.io.Tcp
import akka.io.Tcp.ConnectionClosed
import akka.io.TcpMessage
import com.google.protobuf.InvalidProtocolBufferException
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.messages.AuthProtos
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier.AUTH_CHECK
import net.bestia.zoneserver.actor.BQualifier.CLIENT_CONNECTION_MANAGER
import net.bestia.zoneserver.actor.client.ClientConnectedEvent
import net.bestia.zoneserver.actor.client.ClusterClientConnectionManagerActor
import org.springframework.beans.factory.annotation.Qualifier
import java.nio.ByteBuffer

private val LOG = KotlinLogging.logger { }

/**
 * This actor holds the connection details of a client and is able to redirect
 * messages towards this client. It keeps track of the latency checks and
 * possibly disconnects the client if it does not reply in time.
 *
 * The connection actor also periodically sends out messages towards the client
 * in order to receive ping replies (and to measure latency). The answer tough
 * are managed via a [LatencyManagerActor] who will save the last reply
 * and calculate the current latency.
 *
 * @author Thomas Felix
 */
@Actor
final class SocketActor(
    private val connection: ActorRef,
    @Qualifier(AUTH_CHECK)
    private val authenticationCheckActor: ActorRef
) : AbstractActor() {

  private val buffer = ByteBuffer.allocate(1024 * 4)

  private lateinit var clusterClientConnectionManager: ActorRef

  private val authenticatedSocket: Receive

  init {
    // Stop this actor if the client closes the socket.
    context.watch(connection)

    authenticatedSocket = receiveBuilder()
        .match(Tcp.Received::class.java, this::receiveClientMessage)
        .match(AccountMessage::class.java, this::sendClientMessage)
        .match(ConnectionClosed::class.java) { context.stop(self) }
        .build()
  }

  override fun preStart() {
    clusterClientConnectionManager = ClusterClientConnectionManagerActor.getProxyRef(context)
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(Tcp.Received::class.java, this::waitForAuthMessage)
        .match(AuthResponse::class.java, this::checkAuthResponse)
        .match(ConnectionClosed::class.java) { context.stop(self) }
        .build()
  }

  private fun checkAuthResponse(msg: AuthResponse) {
    if (msg.response == LoginResponse.SUCCESS) {
      announceNewClientConnection(msg.accountId)
    } else {
      LOG.info { "Client send invalid login or server does not allow login. Disconnecting client." }
      context.stop(self)
    }
  }

  private fun waitForAuthMessage(msg: Tcp.Received) {
    val authMsgBytes = extractMessageBytes(msg)
    try {
      val authMessage = AuthProtos.Auth.parseFrom(authMsgBytes)
      val authRequest = AuthRequest(
          accountId = authMessage.accountId,
          token = authMessage.token
      )
      authenticationCheckActor.tell(authRequest, self)

    } catch (e: InvalidProtocolBufferException) {
      LOG.info { "Could not parse auth message. Disconnecting client." }
      context.stop(self)
    }
  }

  private fun extractMessageBytes(msg: Tcp.Received): ByteArray? {
    buffer.put(msg.data().asByteBuffer())
    if (buffer.position() < Int.SIZE_BYTES) {
      return null
    }
    val messageSize = buffer.getInt(0)
    LOG.debug { "Buffer: ${buffer.position()} bytes, next message size: $messageSize bytes" }
    if (buffer.position() < messageSize) {
      return null
    }

    buffer.position(Int.SIZE_BYTES)
    val messageBuffer = buffer.slice()
    val messageBytes = ByteArray(messageSize)
    messageBuffer.get(messageBytes)
    messageBuffer.position(0)

    return messageBytes
  }

  override fun postStop() {
    super.postStop()
    connection.tell(TcpMessage.close(), self)
  }

  private fun announceNewClientConnection(accountId: Long) {
    val event = ClientConnectedEvent(
        accountId = accountId,
        socketActor = self
    )

    clusterClientConnectionManager.tell(event, self)
  }

  private fun sendClientMessage(msg: AccountMessage) {
    LOG.info { "Send: $msg" }
  }

  private fun receiveClientMessage(msg: Tcp.Received) {
    extractMessageBytes(msg)?.let {
      LOG.info { "Received: $it" }
    }
  }
}