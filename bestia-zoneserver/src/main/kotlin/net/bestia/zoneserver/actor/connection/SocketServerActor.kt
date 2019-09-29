package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import akka.actor.Terminated
import akka.io.Tcp
import akka.io.TcpMessage
import mu.KotlinLogging
import net.bestia.zoneserver.AkkaCluster
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.bootstrap.NodeBootstrapActor
import java.net.InetSocketAddress

private val LOG = KotlinLogging.logger { }

/**
 * Keeps and manages the socket connection to a client.
 */
@Actor
class SocketServerActor(
    private val socketConfig: SocketConfig
) : AbstractActor() {

  private var currentConnection = 0

  private fun tellBootstrapManager(msg: Any) {
    val bootStrapPath = AkkaCluster.getNodeName(NodeBootstrapActor.NAME)
    val selector = context.actorSelection(bootStrapPath)
    selector.tell(msg, self)
  }

  @Throws(Exception::class)
  override fun preStart() {
    // Register for updates from the boot manager
    tellBootstrapManager(NodeBootstrapActor.RegisterForBootReport(SocketServerActor::class.java))

    val tcp = Tcp.get(context.system).manager()
    val socketAddress = InetSocketAddress(socketConfig.bindAddress, socketConfig.port)
    tcp.tell(TcpMessage.bind(self, socketAddress, 100), self)
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(NodeBootstrapActor.BootCompleted::class.java) { context.become(readyForConnections()) }
        .match(Tcp.Bound::class.java, this::onTcpBound)
        .build()
  }

  private fun onTcpBound(msg: Tcp.Bound) {
    LOG.info { "Successful bound address to: ${msg.localAddress()}" }
    // Notify boot manager about success
    tellBootstrapManager(NodeBootstrapActor.BootReportSuccess(SocketServerActor::class.java))
  }

  private fun readyForConnections(): Receive {
    return receiveBuilder()
        .match(Tcp.CommandFailed::class.java) { context.stop(self) }
        .match(Tcp.Connected::class.java, this::acceptConnection)
        .match(Terminated::class.java, this::onChildActorConnectionClosed)
        .build()
  }

  private fun onChildActorConnectionClosed(msg: Terminated) {
    // For now we assume that we only get terminated messages for child connections
    currentConnection--
  }

  private fun acceptConnection(conn: Tcp.Connected) {
    val connectionActor = sender
    if (currentConnection >= socketConfig.maxConnections) {
      LOG.info { "Dropping connection request: connection limit reached" }
      connectionActor.tell(TcpMessage.abort(), self)
      return
    }

    currentConnection++
    val handler = SpringExtension.actorOf(context, SocketActor::class.java, connectionActor)
    context.watch(handler)
    connectionActor.tell(TcpMessage.register(handler), self)
    LOG.debug { "Connected client: ${conn.remoteAddress()}" }
  }
}
