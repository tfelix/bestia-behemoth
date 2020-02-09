package net.bestia.zoneserver.actor.client

import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.cluster.singleton.ClusterSingletonProxy
import akka.cluster.singleton.ClusterSingletonProxySettings
import akka.persistence.AbstractPersistentActor
import akka.persistence.SaveSnapshotSuccess
import akka.persistence.SnapshotOffer
import net.bestia.zoneserver.actor.Actor

data class ClientConnectedEvent(
    val accountId: Long,
    val socketActor: ActorRef
)

data class ClientDisconnectedEvent(
    val accountId: Long
)

data class ClientSocketRequest(
    val accountId: Long,
    val originalSender: ActorRef,
    val originalMessage: Any
)

data class ClientSocketResponse(
    val accountId: Long,
    val originalSender: ActorRef,
    val socketActor: ActorRef?
)

/**
 * This is the cluster wide connection manager. It keeps track of all the current connection to the cluster.
 * The nodes will have their own NodeClientConnectionManager which will be queried first if there is data to
 * be send to a client. But if they get a client request they dont know they will request this connection actor
 * here.
 */
@Actor
class ClusterClientConnectionManagerActor : AbstractPersistentActor() {

  private val connections = mutableMapOf<Long, ActorRef>()
  private var updateCount = 0

  override fun createReceiveRecover(): Receive {
    return receiveBuilder()
        .match(ClientConnectedEvent::class.java, this::addClientConnection)
        .match(ClientDisconnectedEvent::class.java, this::removeClientConnection)
        .match(SnapshotOffer::class.java) {
          connections.clear()
          @Suppress("UNCHECKED_CAST")
          val snapshot = it.snapshot() as Map<Long, ActorRef>
          connections.putAll(snapshot)
        }.build()
  }

  /**
   * Make sure this manager is a singelton so we can actually use its name as the persistence id.
   */
  override fun persistenceId(): String {
    return NAME
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ClientConnectedEvent::class.java, this::addClientConnection)
        .match(ClientDisconnectedEvent::class.java, this::removeClientConnection)
        .match(ClientSocketRequest::class.java, this::requestClientSocket)
        .match(SaveSnapshotSuccess::class.java, this::onSnapshotSuccess)
        .build()
  }

  private fun onSnapshotSuccess(msg: SaveSnapshotSuccess) {
    val meta = msg.metadata()
    deleteMessages(meta.sequenceNr())
    // TODO Delete upon a special sequence number
  }

  private fun requestClientSocket(msg: ClientSocketRequest) {
    val response = ClientSocketResponse(
        accountId = msg.accountId,
        socketActor = connections[msg.accountId],
        originalSender = msg.originalSender
    )

    sender.tell(response, self)
  }

  private fun addClientConnection(msg: ClientConnectedEvent) {
    persist(msg) {
      connections[msg.accountId]?.let { existingSocketActor ->
        if (existingSocketActor != msg.socketActor) {
          existingSocketActor.tell(PoisonPill.getInstance(), self)
        }
      }
      connections[msg.accountId] = msg.socketActor
      updateCount++
    }
  }

  private fun removeClientConnection(msg: ClientDisconnectedEvent) {
    persist(msg) {
      connections.remove(msg.accountId)
      updateCount++
    }
  }

  companion object {
    const val NAME = "clusterClientConnectionManager"
    // Perform an actor snapshot after 1000 updates.
    private const val SNAPSHOT_AFTER_UPDATES = 1000

    fun getProxyRef(ctx: ActorContext): ActorRef {
      val proxySettings = ClusterSingletonProxySettings.create(ctx.system)
      val props = ClusterSingletonProxy.props("/user/${ClusterClientConnectionManagerActor.NAME}", proxySettings)

      return ctx.actorOf(props, "clientConnectionProxy")
    }
  }
}