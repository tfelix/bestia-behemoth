package net.bestia.zoneserver.actor.bootstrap

import akka.actor.AbstractActor
import akka.cluster.Cluster
import akka.cluster.ClusterEvent
import mu.KotlinLogging
import net.bestia.zoneserver.AkkaCluster
import net.bestia.zoneserver.actor.Actor

private val LOG = KotlinLogging.logger { }

/**
 * Watches the cluster state and also signals the BootStrap actor when to orchestrate further
 * bootstrapping
 */
@Actor
class ClusterMonitorActor : AbstractActor() {

  private fun tellNoodBootstrapManager(msg: Any) {
    val bootStrapPath = AkkaCluster.getNodeName(NodeBootstrapActor.NAME)
    val selector = context.actorSelection(bootStrapPath)
    selector.tell(msg, self)
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ClusterEvent.CurrentClusterState::class.java, this::onCurrentClusterState)
        .match(ClusterEvent.ClusterDomainEvent::class.java, this::onClusterEvent)
        .build()
  }

  override fun preStart() {
    Cluster.get(context.system).subscribe(self, ClusterEvent.ClusterDomainEvent::class.java)
  }

  private fun onCurrentClusterState(msg: ClusterEvent.CurrentClusterState) {
    LOG.debug { "Current Cluster State: $msg" }
  }

  private fun onClusterEvent(msg: ClusterEvent.ClusterDomainEvent) {
    when (msg) {
      is ClusterEvent.MemberUp -> {
        val selfMember = Cluster.get(context.system).selfMember()
        if (msg.member() == selfMember) {
          // we joined the cluster and can report success to continue with boot sequence
          tellNoodBootstrapManager(NodeBootstrapActor.BootReportSuccess(ClusterMonitorActor::class.java))
        }
      }
      else -> {
        LOG.debug { "Cluster Event: $msg" }
      }
    }
  }

  companion object {
    const val NAME = "clusterMonitor"
  }
}