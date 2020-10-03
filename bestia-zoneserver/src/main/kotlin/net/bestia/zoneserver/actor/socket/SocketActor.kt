package net.bestia.zoneserver.actor.socket

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.io.Tcp
import akka.io.Tcp.ConnectionClosed
import akka.io.TcpMessage
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier.AUTH_CHECK
import net.bestia.zoneserver.actor.BQualifier.CLIENT_MESSAGE_ROUTER
import net.bestia.zoneserver.actor.client.ClientConnectedEvent
import net.bestia.zoneserver.actor.client.ClusterClientConnectionManagerActor
import net.bestia.zoneserver.messages.MessageConvertException
import net.bestia.zoneserver.messages.MessageConverterService
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
    private val authenticationCheckActor: ActorRef,
    @Qualifier(CLIENT_MESSAGE_ROUTER)
    private val messageRouter: ActorRef,
    private val messageConverter: MessageConverterService
) : AbstractActor() {

  private val buffer = ByteBuffer.allocate(MAX_MESSAGE_SIZE)
  private var nextPackageSize: Int = 0

  private lateinit var clusterClientConnectionManager: ActorRef

  private val authenticatedSocket: Receive

  init {
    // Stop this actor if the client closes the socket.
    context.watch(connection)

    authenticatedSocket = receiveBuilder()
        .match(Tcp.Received::class.java, this::receiveClientMessage)
        .match(AccountMessage::class.java, this::sendToClient)
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

  private fun authenticated(): Receive {
    return receiveBuilder()
        .match(Tcp.Received::class.java, this::onClientMessage)
        .match(ConnectionClosed::class.java) { context.stop(self) }
        .build()
  }

  private fun checkAuthResponse(msg: AuthResponse) {
    when (msg.response) {
      LoginResponse.SUCCESS -> {
        context.become(authenticated())
        announceNewClientConnection(msg.accountId)
      }
      LoginResponse.UNAUTHORIZED, LoginResponse.NO_LOGINS_ALLOWED -> {
        LOG.info { "Client send invalid login or server does not allow login: $msg. Disconnecting client." }
        context.stop(self)
      }
    }
    // Make sure this is invoced even after the context stop self
    val payload = messageConverter.convertToPayload(msg)
    sendToClient(payload)
  }

  private fun waitForAuthMessage(msg: Tcp.Received) {
    try {
      val authMsgBytes = extractMessageBytes(msg)
          ?: return
      val authMessage = messageConverter.convertToMessage(authMsgBytes)
          ?: return

      if (authMessage !is AuthRequest) {
        context.stop(self)
        return
      }

      authenticationCheckActor.tell(authMessage, self)
    } catch (e: MessageConvertException) {
      LOG.info { "Could not parse auth message. Disconnecting client." }
      context.stop(self)
    }
  }

  private fun onClientMessage(msg: Tcp.Received) {
    try {
      val msgBytes = extractMessageBytes(msg)
          ?: return
      val message = messageConverter.convertToMessage(msgBytes)
          ?: return
      messageRouter.tell(message, self)
    } catch (e: MessageConvertException) {
      LOG.info(e) { "Could not parse auth message. Disconnecting client." }
      context.stop(self)
    }
  }

  private fun extractMessageBytes(msg: Tcp.Received): ByteArray? {
    buffer.put(msg.data().asByteBuffer())
    if (buffer.position() < Int.SIZE_BYTES) {
      return null
    }

    if (nextPackageSize == 0) {
      nextPackageSize = buffer.getInt(0)

      if (nextPackageSize > MAX_MESSAGE_SIZE) {
        throw IllegalStateException("Client requested message size $nextPackageSize, max size is $MAX_MESSAGE_SIZE")
      }
    }

    LOG.debug { "Current buffer size: ${buffer.position()} bytes, next message size: $nextPackageSize bytes" }

    if (buffer.position() < nextPackageSize) {
      return null
    }

    buffer.position(Int.SIZE_BYTES)
    val messageBuffer = buffer.slice()
    val messageBytes = ByteArray(nextPackageSize)

    messageBuffer.get(messageBytes)
    buffer.position(0)
    nextPackageSize = 0

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

  private fun sendToClient(data: ByteArray) {
    connection.tell(data.toTcpMessage(), self)
  }

  private fun sendToClient(msg: AccountMessage) {
    LOG.info { "Send: $msg" }
    val output = messageConverter.convertToPayload(msg)
    connection.tell(output.toTcpMessage(), self)
  }

  private fun receiveClientMessage(msg: Tcp.Received) {
    extractMessageBytes(msg)?.let {
      LOG.trace { "Received: $it" }
    }
  }

  companion object {
    private const val MAX_MESSAGE_SIZE = 1024 * 4 // 4kb
  }
}