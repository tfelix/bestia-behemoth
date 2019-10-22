package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.io.Tcp
import akka.io.Tcp.ConnectionClosed
import akka.io.TcpMessage
import mu.KotlinLogging
import net.bestia.messages.AuthMessageProto
import net.bestia.messages.login.LoginAuthRequestMessage
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
class SocketActor(
    private val connection: ActorRef,
    private val authenticationService: AuthenticationService,
    private val loginService: LoginService,
    @Qualifier(AkkaConfiguration.CONNECTION_MANAGER)
    private val clusterClientConnectionManager: ActorRef
) : AbstractActor() {

  private val buffer = ByteBuffer.allocate(1024 * 4)

  init {
    // this actor stops when the connection is closed
    context.watch(connection)
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(Tcp.Received::class.java, this::waitForAuthMessage)
        .match(ConnectionClosed::class.java) { context.stop(self) }
        .build()
  }

  /**
   * State if the connection became authenticated with the client.
   */
  private fun authenticated(): Receive {
    return receiveBuilder()
        .match(Tcp.Received::class.java, this::receiveClientMessage)
        .match(ConnectionClosed::class.java) { context.stop(self) }
        .build()
  }

  private fun waitForAuthMessage(msg: Tcp.Received) {
    buffer.put(msg.data().asByteBuffer())

    if (buffer.position() < Int.SIZE_BYTES) {
      return
    }

    val messageSize = buffer.getInt(0)

    LOG.debug { "Buffer: ${buffer.position()} bytes, next message size: $messageSize bytes" }

    if (buffer.position() < messageSize) {
      return
    }

    val messageBuffer = buffer.slice()
    val messageBytes = ByteArray(messageSize)
    messageBuffer.get(messageBytes)
    messageBuffer.position(0)

    val authMessage = AuthMessageProto.AuthMessage.parseFrom(messageBytes)
    LOG.debug { authMessage }

    val isAuthenticated = authenticationService.isUserAuthenticated(
        authMessage.accountId,
        authMessage.token
    )
    val isLoginAllowed = loginService.isLoginAllowedForAccount(authMessage.accountId)

    if (isAuthenticated && isLoginAllowed) {
      context.become(authenticated(), true)
      announceNewClientConnection(authMessage.accountId)
    } else {
      // TODO Check if we need so send some kind of reason first. Currently in maintenance mode?
      connection.tell(TcpMessage.close(), self)
    }
  }

  override fun postStop() {
    super.postStop()
    LOG.trace { "Actor stopped, closing socket" }
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
    // Deserialize and feed it into the system.
    LOG.debug { "Received: ${msg.data()}" }
  }
}