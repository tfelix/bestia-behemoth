package net.bestia.zoneserver.actor.client

import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.cluster.singleton.ClusterSingletonProxy
import akka.cluster.singleton.ClusterSingletonProxySettings
import akka.persistence.*
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BSerializable
import scala.Option
import java.io.InvalidClassException

private val LOG = KotlinLogging.logger { }

data class ClientConnectedEvent(
    val accountId: Long,
    val socketActor: ActorRef
) : BSerializable

data class ClientDisconnectedEvent(
    val accountId: Long
) : BSerializable

data class ClientSocketRequest(
    val accountId: Long,
    val originalSender: ActorRef,
    val originalMessage: Any
) : BSerializable

data class ClientSocketResponse(
    val accountId: Long,
    val originalMessage: Any,
    val originalSender: ActorRef,
    val socketActor: ActorRef?
) : BSerializable

/**
 * This is the cluster wide connection manager. It keeps track of all the current connection to the cluster.
 * The nodes will have their own NodeClientConnectionManager which will be queried first if there is data to
 * be send to a client. But if they get a client request they don't know they will request this connection actor
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

  override fun recovery(): Recovery {
    return Recovery.none()
  }

  override fun onRecoveryFailure(cause: Throwable, event: Option<Any>) {
    if (cause is InvalidClassException) {
      LOG.warn(cause) { "Class was probably changed and can not be recovered. Trying to recover by deleting journal." }
      deleteSnapshots(SnapshotSelectionCriteria.latest())
      return
    }
    super.onRecoveryFailure(cause, event)
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
    LOG.trace { "Received: $msg" }

    val meta = msg.metadata()
    deleteMessages(meta.sequenceNr())
    // TODO Delete upon a special sequence number
  }

  private fun requestClientSocket(msg: ClientSocketRequest) {
    LOG.trace { "Received: $msg" }

    val response = ClientSocketResponse(
        accountId = msg.accountId,
        socketActor = connections[msg.accountId],
        originalSender = msg.originalSender,
        originalMessage = msg.originalMessage
    )

    sender.tell(response, self)
  }

  private fun addClientConnection(msg: ClientConnectedEvent) {
    LOG.trace { "Received: $msg" }

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
    LOG.trace { "Received: $msg" }

    persist(msg) {
      connections.remove(msg.accountId)
      updateCount++
    }
  }

  companion object {
    const val NAME = "clusterClientConnectionManager"

    fun getProxyRef(ctx: ActorContext): ActorRef {
      val proxySettings = ClusterSingletonProxySettings.create(ctx.system)
      val props = ClusterSingletonProxy.props("/user/${ClusterClientConnectionManagerActor.NAME}", proxySettings)

      return ctx.actorOf(props, "clientConnectionProxy")
    }
  }
}