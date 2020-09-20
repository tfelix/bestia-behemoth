package net.bestia.zoneserver.actor.socket

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Terminated
import akka.io.Tcp
import akka.io.TcpMessage
import mu.KotlinLogging
import net.bestia.zoneserver.AkkaCluster
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.bootstrap.NodeBootstrapActor
import net.bestia.zoneserver.actor.SocketBindNetworkError
import org.springframework.beans.factory.annotation.Qualifier
import java.net.InetSocketAddress

private val LOG = KotlinLogging.logger { }

/**
 * Keeps and manages the socket connection to clients. It accepts new incoming
 * connections and prepares the socket actor instances.
 */
@Actor
class SocketServerActor(
    private val socketConfig: SocketConfig,
    @Qualifier(BQualifier.SYSTEM_ROUTER)
    private val systemRouter: ActorRef
) : AbstractActor() {

  private var currentConnection = 0
  private val socketAddress = InetSocketAddress(socketConfig.bindAddress, socketConfig.port)

  private fun tellNodeBootstrapManager(msg: Any) {
    val bootStrapPath = AkkaCluster.getNodeName(NodeBootstrapActor.NAME)
    val selector = context.actorSelection(bootStrapPath)
    selector.tell(msg, self)
  }

  @Throws(Exception::class)
  override fun preStart() {
    // Register for updates from the boot manager
    tellNodeBootstrapManager(NodeBootstrapActor.RegisterForBootCompleted(self))

    val tcp = Tcp.get(context.system).manager()

    LOG.info { "Try to bind to $socketAddress" }
    tcp.tell(TcpMessage.bind(self, socketAddress, 100), self)
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(NodeBootstrapActor.BootCompleted::class.java) {
          LOG.info { "Server is now ready to accept client connections" }
          context.become(readyForConnections())
        }
        .match(Tcp.Bound::class.java, this::onTcpBound)
        .match(Tcp.CommandFailed::class.java, this::onTcpBoundFail)
        .match(Tcp.Connected::class.java, this::dropConnection)
        .build()
  }

  private fun onTcpBoundFail(msg: Tcp.CommandFailed) {
    LOG.error { "Could not bind to $msg" }
    systemRouter.tell(SocketBindNetworkError, self)
  }

  private fun onTcpBound(msg: Tcp.Bound) {
    LOG.info { "Bound address to: $msg" }
    // Notify boot manager about success to proceed boot process
    tellNodeBootstrapManager(NodeBootstrapActor.BootReportSuccess(SocketServerActor::class.java))
  }

  private fun readyForConnections(): Receive {
    return receiveBuilder()
        .match(Tcp.CommandFailed::class.java) { context.stop(self) }
        .match(Tcp.Connected::class.java, this::acceptConnection)
        .match(Terminated::class.java, this::onChildActorConnectionClosed)
        .build()
  }

  private fun onChildActorConnectionClosed(msg: Terminated) {
    LOG.debug { "Connection closed to $msg" }
    // For now we assume that we only get terminated messages for child connections
    currentConnection--
  }

  private fun dropConnection(conn: Tcp.Connected) {
    LOG.debug { "Not ready yet for connections. Closing: $conn" }
    sender.tell(TcpMessage.close(), self)
  }

  private fun acceptConnection(conn: Tcp.Connected) {
    val connectionActor = sender
    if (currentConnection >= socketConfig.maxConnections) {
      LOG.info { "Dropping connection request: connection limit reached" }
      connectionActor.tell(TcpMessage.close(), self)
      return
    }

    currentConnection++
    val actorName = "client-${conn.remoteAddress().hostString}-${conn.remoteAddress().port}"
    val handler = SpringExtension.actorOfWithName(
        context,
        SocketActor::class.java,
        actorName,
        connectionActor
    )
    context.watch(handler)
    connectionActor.tell(TcpMessage.register(handler), self)
    LOG.debug { "Connected client: $conn" }
  }

  companion object {
    const val NAME = "socketServer"
  }
}
