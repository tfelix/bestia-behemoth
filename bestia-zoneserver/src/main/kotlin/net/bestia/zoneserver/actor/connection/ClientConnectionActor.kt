package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Terminated
import mu.KotlinLogging
import net.bestia.messages.MessageId
import net.bestia.messages.client.ClientConnectMessage
import net.bestia.messages.client.ClientDisconnectMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.account.LoginService
import net.bestia.zoneserver.account.LogoutService
import net.bestia.zoneserver.actor.ActorComponent
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
@ActorComponent
class ClientConnectionActor(
    private val loginService: LoginService,
    private val logoutService: LogoutService
) : AbstractActor() {

  private var accountId: Long = 0
  private var authenticatedSocket: ActorRef? = null

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(ClientEnvelope::class.java, this::checkMessageEnvelope)
        .match(MessageId::class.java, this::sendMessageToClient)
        .match(Terminated::class.java) { onClientConnectionClosed() }
        .build()
  }

  @Throws(Exception::class)
  override fun postStop() {
    logoutService.logout(accountId)
    LOG.debug("Connection removed: {}, account: {}", self.path(), accountId)
  }

  /**
   * There are a few messages which are ment for this actor which might be only wrapped
   * for convienence. So we check this here.
   */
  private fun checkMessageEnvelope(msg: ClientEnvelope) {
    val content = msg.content
    when (content) {
      is ClientConnectMessage -> handleConnect(content)
      is ClientDisconnectMessage -> handleDisconnect(content)
      else -> sendMessageToClient(msg.content)
    }
  }

  private fun handleConnect(msg: ClientConnectMessage) {
    LOG.debug("Client has connected: {}.", msg)

    accountId = msg.accountId

    authenticatedSocket?.let {
      context.unwatch(it)
      it.tell(PoisonPill.getInstance(), self)
    }

    authenticatedSocket = msg.webserverRef
    context.watch(authenticatedSocket)

    initClientConnection(msg)
  }

  private fun handleDisconnect(msg: ClientDisconnectMessage) {
    LOG.debug("Client has disconnected: {}.", msg)
    context.stop(self)
  }

  /**
   * Initializes a client connection.
   */
  private fun initClientConnection(msg: ClientConnectMessage) {
    LOG.debug("Client has authenticated: {}.", msg)
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