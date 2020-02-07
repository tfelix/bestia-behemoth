package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.cluster.singleton.ClusterSingletonProxy
import akka.cluster.singleton.ClusterSingletonProxySettings
import net.bestia.zoneserver.actor.Actor

/**
 * Caches the client socket refs for the node so no cluster wide lookup must be performed
 * when needed.
 */
@Actor
class SendClientActor : AbstractActor() {

  private lateinit var clusterConnectionManager: ActorRef
  private val connections = mutableMapOf<Long, ActorRef>()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ClientConnectedEvent::class.java, this::addClientConnection)
        .match(ClientDisconnectedEvent::class.java, this::removeClientConnection)
        .match(ClientSocketRequest::class.java, this::requestClientSocket)
        .match(ClientSocketResponse::class.java, this::receiveUpstreamSocketResponse)
        .build()
  }

  override fun preStart() {
    val proxySettings = ClusterSingletonProxySettings.create(context.system)
    val props = ClusterSingletonProxy.props("/user/${ClusterClientConnectionManagerActor.NAME}", proxySettings)

    context.actorOf(props, "clientConnectionProxy")
  }

  private fun receiveUpstreamSocketResponse(msg: ClientSocketResponse) {
    if (msg.socketActor != null) {
      connections[msg.accountId] = msg.socketActor
    }

    msg.originalSender.tell(msg, self)
  }

  private fun requestClientSocket(msg: ClientSocketRequest) {
    connections[msg.accountId]?.let { socketActor ->
      val response = ClientSocketResponse(
          accountId = msg.accountId,
          socketActor = socketActor,
          originalSender = msg.originalSender
      )

      msg.originalSender.tell(response, self)
      return
    }

    clusterConnectionManager.tell(msg, self)
  }

  private fun addClientConnection(msg: ClientConnectedEvent) {
    connections[msg.accountId] = msg.socketActor
    clusterConnectionManager.tell(msg, self)
  }

  private fun removeClientConnection(msg: ClientDisconnectedEvent) {
    connections.remove(msg.accountId)
    clusterConnectionManager.tell(msg, self)
  }

  companion object {
    const val NAME = "sendToClient"
  }
}