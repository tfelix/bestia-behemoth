package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.PoisonPill
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import mu.KotlinLogging
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.EntryActorNames
import net.bestia.zoneserver.TestService
import net.bestia.zoneserver.actor.client.ClientMessageActor
import net.bestia.zoneserver.actor.connection.ClientConnectionActor
import net.bestia.zoneserver.actor.connection.ConnectionShardMessageExtractor
import net.bestia.zoneserver.actor.connection.IngestActor
import net.bestia.zoneserver.actor.entity.EntityActor
import net.bestia.zoneserver.actor.entity.EntityIdGeneratorActor
import net.bestia.zoneserver.actor.entity.EntityShardMessageExtractor
import net.bestia.zoneserver.actor.entity.component.ComponentBroadcastEnvelope
import net.bestia.zoneserver.actor.entity.component.RequestComponentMessage
import net.bestia.zoneserver.actor.routing.RoutingActor
import net.bestia.zoneserver.script.ScriptService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Central root actor of the bestia zone hierarchy.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class BestiaRootActor(
        private val scriptService: ScriptService,
        private val testService: TestService
) : AbstractActor() {

  private val system = context.system()
  private val settings = ClusterShardingSettings.create(system)
  private val sharding = ClusterSharding.get(system)

  override fun createReceive(): Receive {
    return emptyBehavior()
  }

  override fun preStart() {
    LOG.info { "Bootstrapping Behemoth actor system." }

    // === Client Messages ===
    SpringExtension.actorOf(context, ClientMessageActor::class.java)

    registerSingeltons()
    // registerSharding()

    // Call the startup script.
    scriptService.callScriptMainFunction("startup")

    // Register the cluster client receptionist for receiving messages.
    val ingest = SpringExtension.actorOf(context, IngestActor::class.java)
    val receptionist = ClusterClientReceptionist.get(system)
    receptionist.registerService(ingest)

    // FIXME Wieder entfernen wenn ich fertig bin
    test()
  }

  private fun registerSharding() {
    LOG.info { "Configuring sharding actors." }

    LOG.info { "Starting entity sharding..." }
    val entityProps = SpringExtension.getSpringProps(system, EntityActor::class.java)
    val entityExtractor = EntityShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_ENTITY, entityProps, settings, entityExtractor)

    LOG.info { "Starting client sharding..." }
    val connectionProps = SpringExtension.getSpringProps(system, ClientConnectionActor::class.java)
    val connectionExtractor = ConnectionShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_CONNECTION, connectionProps, settings, connectionExtractor)

    LOG.info("Started the sharding actors.")
  }

  private fun registerSingeltons() {
    LOG.info { "Starting the bootstrap actor." }

    val system = context.system()
    val settings = ClusterSingletonManagerSettings.create(system)

    startAsSingelton(settings, BootstrapActor::class.java, "bootstrap")
    startAsSingelton(settings, EntityIdGeneratorActor::class.java, EntityIdGeneratorActor.NAME)

    LOG.info { "Started the bootstrap actor." }
  }

  private fun <T : AbstractActor> startAsSingelton(
          settings: ClusterSingletonManagerSettings,
          actorClass: Class<T>,
          name: String
  ) {
    val props = SpringExtension.getSpringProps(system, actorClass)
    val clusterProbs = ClusterSingletonManager.props(props, PoisonPill.getInstance(), settings)
    system.actorOf(clusterProbs, name)
  }

  private fun test() {
    val routerActor = SpringExtension.actorOf(system, RoutingActor::class.java)
    val msg = RequestComponentMessage(self())
    val broadcast = ComponentBroadcastEnvelope(msg)
    val entityMsg = EntityEnvelope(1L, broadcast)
    routerActor.tell(entityMsg, self())

    val id = testService.addComponent("blabla")
    testService.updateComponent(id, "unddazu")
  }

  companion object {
    const val NAME = "bestia"
  }
}
