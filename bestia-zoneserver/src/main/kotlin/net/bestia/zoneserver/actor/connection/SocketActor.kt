package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.io.Tcp
import akka.io.Tcp.ConnectionClosed
import akka.io.TcpMessage
import com.google.protobuf.InvalidProtocolBufferException
import mu.KotlinLogging
import net.bestia.messages.AuthMessageProto
import net.bestia.zoneserver.account.AuthenticationService
import net.bestia.zoneserver.account.LoginService
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.AkkaConfiguration
import net.bestia.zoneserver.actor.client.ClientConnectedEvent
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
    private val authenticationService: AuthenticationService,
    private val loginService: LoginService,
    @Qualifier(AkkaConfiguration.CONNECTION_MANAGER)
    private val clusterClientConnectionManager: ActorRef
) : AbstractActor() {

  private val buffer = ByteBuffer.allocate(1024 * 4)

  private val authenticatedSocket: Receive

  init {
    // Stop this actor if the client closes the socket.
    context.watch(connection)

    authenticatedSocket = receiveBuilder()
        .match(Tcp.Received::class.java, this::receiveClientMessage)
        .match(ConnectionClosed::class.java) { context.stop(self) }
        .build()
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(Tcp.Received::class.java, this::waitForAuthMessage)
        .match(ConnectionClosed::class.java) { context.stop(self) }
        .build()
  }

  private fun waitForAuthMessage(msg: Tcp.Received) {
    val authMsgBytes = extractMessageBytes(msg)
    try {
      val authMessage = AuthMessageProto.AuthMessage.parseFrom(authMsgBytes)
      LOG.trace { "Received auth: $authMessage" }

      val isAuthenticated = authenticationService.isUserAuthenticated(
          authMessage.accountId,
          authMessage.token
      )
      val isLoginAllowed = loginService.isLoginAllowedForAccount(authMessage.accountId, authMessage.token)

      if (isAuthenticated && isLoginAllowed) {
        context.become(authenticatedSocket, true)
        announceNewClientConnection(authMessage.accountId)
      } else {
        LOG.info { "Client send invalid login or server does not allow login. Disconnecting client." }
        context.stop(self)
      }
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

  private fun receiveClientMessage(msg: Tcp.Received) {
    extractMessageBytes(msg)?.let {
      LOG.info { "Received: $it" }
    }
  }
}