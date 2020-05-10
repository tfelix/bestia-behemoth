package net.bestia.zoneserver.actor.socket

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.io.Tcp
import akka.io.Tcp.ConnectionClosed
import akka.io.TcpMessage
import com.google.protobuf.InvalidProtocolBufferException
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.messages.proto.AuthProto
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier.AUTH_CHECK
import net.bestia.zoneserver.actor.client.ClientConnectedEvent
import net.bestia.zoneserver.actor.client.ClusterClientConnectionManagerActor
import net.bestia.zoneserver.messages.ProtobufMessageConverterService
import org.springframework.beans.factory.annotation.Qualifier
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    private val authenticationCheckActor: ActorRef,
    private val messageConverter: ProtobufMessageConverterService
) : AbstractActor() {

  private val buffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE)

  private lateinit var clusterClientConnectionManager: ActorRef

  private val authenticatedSocket: Receive

  init {
    // Stop this actor if the client closes the socket.
    context.watch(connection)

    authenticatedSocket = receiveBuilder()
        .match(Tcp.Received::class.java, this::receiveClientMessage)
        .match(AccountMessage::class.java, this::sendToClientMessage)
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
      LOG.info { "Client send invalid login or server does not allow login: $msg. Disconnecting client." }
      context.stop(self)
    }
  }

  private fun waitForAuthMessage(msg: Tcp.Received) {
    val authMsgBytes = extractMessageBytes(msg)
    try {
      val authMessage = AuthProto.Auth.parseFrom(authMsgBytes)
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
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val messageSize = buffer.getInt(0)

    LOG.debug { "Current buffer: ${buffer.position()} bytes, next message size: $messageSize bytes" }

    if (messageSize > MAX_MESSAGE_SIZE) {
      throw IllegalStateException("Client requested message size $messageSize, max size is $MAX_MESSAGE_SIZE")
    }

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

  private fun sendToClientMessage(msg: AccountMessage) {
    LOG.info { "Send: $msg" }
    val output = messageConverter.fromBestia(msg)
    val buffer = akka.util.ByteString.fromArray(output)
    connection.tell(TcpMessage.write(buffer), self)
  }

  private fun receiveClientMessage(msg: Tcp.Received) {
    extractMessageBytes(msg)?.let {
      LOG.info { "Received: $it" }
    }
  }

  companion object {
    private const val MAX_MESSAGE_SIZE = 1024 * 4 // 4kb
  }
}