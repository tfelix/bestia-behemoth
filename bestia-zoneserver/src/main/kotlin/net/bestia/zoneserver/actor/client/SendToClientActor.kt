package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.Actor

private val LOG = KotlinLogging.logger { }

/**
 * Caches the client socket refs for the node so no cluster wide lookup must be performed
 * when needed.
 */
@Actor
class SendToClientActor : AbstractActor() {

  private lateinit var clusterConnectionManager: ActorRef
  private val connections = mutableMapOf<Long, ActorRef>()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ClientConnectedEvent::class.java, this::addClientConnection)
        .match(ClientDisconnectedEvent::class.java, this::removeClientConnection)
        .match(ClientSocketRequest::class.java, this::requestClientSocket)
        .match(ClientSocketResponse::class.java, this::receiveClientSocketResponse)
        .match(AccountMessage::class.java, this::forwardToClientSocket)
        .build()
  }

  override fun preStart() {
    clusterConnectionManager = ClusterClientConnectionManagerActor.getProxyRef(context)
  }

  private fun forwardToClientSocket(msg: AccountMessage) {
    LOG.trace { "Received: $msg" }

    // check if we have the socket actor reference.
    val socketActor = connections[msg.accountId]

    if (socketActor == null) {
      val requestClientSocket = ClientSocketRequest(
          accountId = msg.accountId,
          originalMessage = msg,
          originalSender = self
      )
      clusterConnectionManager.tell(requestClientSocket, self)
      return
    }

    socketActor.tell(msg, self)
  }

  private fun receiveClientSocketResponse(msg: ClientSocketResponse) {
    LOG.trace { "Received: $msg" }

    if (msg.socketActor != null) {
      LOG.debug { "Received socket ${msg.socketActor} for account ${msg.accountId}" }
      connections[msg.accountId] = msg.socketActor
    }

    // FIXME make stype safe
    forwardToClientSocket(msg.originalMessage as AccountMessage)
  }

  private fun requestClientSocket(msg: ClientSocketRequest) {
    LOG.trace { "Received: $msg" }

    connections[msg.accountId]?.let { socketActor ->
      val response = ClientSocketResponse(
          accountId = msg.accountId,
          socketActor = socketActor,
          originalSender = msg.originalSender,
          originalMessage = msg.originalMessage
      )

      msg.originalSender.tell(response, self)
      return
    }

    clusterConnectionManager.tell(msg, self)
  }

  private fun addClientConnection(msg: ClientConnectedEvent) {
    LOG.trace { "Received: $msg" }

    connections[msg.accountId] = msg.socketActor
    clusterConnectionManager.tell(msg, self)
  }

  private fun removeClientConnection(msg: ClientDisconnectedEvent) {
    LOG.trace { "Received: $msg" }

    connections.remove(msg.accountId)
    clusterConnectionManager.tell(msg, self)
  }

  companion object {
    const val NAME = "sendToClient"
  }
}