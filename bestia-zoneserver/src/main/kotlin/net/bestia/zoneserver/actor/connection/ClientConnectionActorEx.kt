package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import mu.KotlinLogging
import net.bestia.messages.ClientToMessageEnvelope
import net.bestia.messages.JsonMessage
import net.bestia.messages.client.ClientConnectMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.client.LoginService
import net.bestia.zoneserver.client.LogoutService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

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
@Component
@Scope("prototype")
class ClientConnectionActorEx @Autowired constructor(
        private val loginService: LoginService,
        private val logoutService: LogoutService
) : AbstractActor() {

  private var accountId: Long = 0
  private var authenticatedSocket: ActorRef? = null

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(ClientToMessageEnvelope::class.java, this::checkMessageEnvelope)
            .match(JsonMessage::class.java, { msg -> sendMessageToClient(msg) })
            .match(Terminated::class.java) { _ -> onClientConnectionClosed() }
            .build()
  }

  @Throws(Exception::class)
  override fun postStop() {

    // Stop connection and clean up the associated entity actors.
    logoutService.logout(accountId)

    LOG.debug("Connection removed: {}, account: {}", self.path(), accountId)
  }

  /**
   * There are a few messages which are ment for this actor which might be only wrapped
   * for convienence. So we check this here.
   */
  private fun checkMessageEnvelope(msg: ClientToMessageEnvelope) {
    val content = msg.content
    when (content) {
      is ClientConnectMessage -> handleNewClientConnection(content)
      else -> sendMessageToClient(msg.content)
    }
  }

  private fun handleNewClientConnection(msg: ClientConnectMessage) {
    LOG.debug("Client has connected: {}.", msg)

    accountId = msg.accountId

    when (msg.state) {
      ClientConnectMessage.ConnectionState.CONNECTED -> {
        authenticatedSocket?.let {
          context.unwatch(it)
          it.tell(PoisonPill.getInstance(), self)
        }

        authenticatedSocket = msg.webserverRef
        context.watch(authenticatedSocket)

        initClientConnection(msg)
      }
      ClientConnectMessage.ConnectionState.DISCONNECTED -> {
        context.stop(self)
      }
      else -> {
      }
    }
  }

  /**
   * Initializes a client connection.
   */
  private fun initClientConnection(msg: ClientConnectMessage) {
    LOG.debug("Client has authenticated: {}.", msg)

    SpringExtension.actorOf(context,
            LatencyPingActor::class.java,
            accountId,
            authenticatedSocket)

    // Spawn all the associated entities.
    loginService.login(accountId)

    LOG.debug("Connection established: {}, account: {}", self.path(), accountId)
  }

  /**
   * Called if the client actor and thus its connection has been terminated.
   * Connection actor must clean the server resources by terminating itself.
   *
   */
  private fun onClientConnectionClosed() {
    LOG.debug("Socket actor account {} has terminated.", accountId)
    context.stop(self)
  }

  /**
   * Message must be forwarded to the client webserver so the message can be
   * received by the client.
   */
  private fun sendMessageToClient(msg: Any) {
    LOG.debug(String.format("Sending to client %d: %s", accountId, msg))

    if (authenticatedSocket == null) {
      LOG.warn { "Can not send to client. Not actorRef set! Stopping. MSG: $msg" }
      context.stop(self)
      return
    }

    authenticatedSocket?.tell(msg, self)
  }

  companion object {
    private const val ACTOR_NAME = "connection-%d"

    /**
     * Gets the unique actor name by its connected account id.
     *
     * @param accId
     * The account ID.
     * @return The unique name of the connection actor.
     */
    @JvmStatic
    fun getActorName(accId: Long): String {
      return String.format(ACTOR_NAME, accId)
    }
  }
}