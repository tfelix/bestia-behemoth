package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import bestia.server.EntryActorNames
import mu.KotlinLogging
import net.bestia.messages.MessageApi
import net.bestia.zoneserver.actor.client.ClientMessageActor
import net.bestia.zoneserver.actor.connection.ClientConnectionActor
import net.bestia.zoneserver.actor.connection.IngestActor
import net.bestia.zoneserver.actor.entity.EntityActor
import net.bestia.zoneserver.actor.routing.ConnectionShardMessageExtractor
import net.bestia.zoneserver.actor.routing.EntityShardMessageExtractor
import net.bestia.zoneserver.actor.routing.PostmasterActor
import net.bestia.zoneserver.actor.zone.BootstrapActor
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
        private val messageApi: MessageApi
) : AbstractActor() {

  private val system = context.system()
  private val settings = ClusterShardingSettings.create(system)
  private val sharding = ClusterSharding.get(system)

  override fun preStart() {

    LOG.info("Bootstrapping Behemoth actor system.")
    val postmaster = SpringExtension.actorOf(context, PostmasterActor::class.java)

    LOG.info("Starting system actors.")
    // Setup the messaging system. This is also needed because the message
    // api is created before the registering takes place
    // thus to break the circular dependency this trick is needed.
    messageApi.setPostmaster(postmaster)

    // === Client Messages ===
    SpringExtension.actorOf(context, ClientMessageActor::class.java, postmaster)

    registerSingeltons()
    registerSharding(postmaster)

    // === Web/REST actors ===
    // SpringExtension.actorOf(getContext(), CheckUsernameDataActor.class);
    // SpringExtension.actorOf(getContext(), ChangePasswordActor.class);
    // SpringExtension.actorOf(getContext(), RequestLoginActor.class);
    // SpringExtension.actorOf(getContext(), RequestServerStatusActor.class);

    // Call the startup script.
    scriptService.callScriptMainFunction("startup")

    // Register the cluster client receptionist for receiving messages.
    val ingest = SpringExtension.actorOf(context, IngestActor::class.java, postmaster)
    val receptionist = ClusterClientReceptionist.get(context.system)
    receptionist.registerService(ingest)
  }

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder().build()
  }

  private fun registerSharding(postmaster: ActorRef) {
    LOG.info{ "Configuring sharding actors." }

    LOG.info{ "Starting entity sharding..." }
    val entityProps = SpringExtension.getSpringProps(system, EntityActor::class.java)
    val entityExtractor = EntityShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_ENTITY, entityProps, settings, entityExtractor)

    LOG.info{ "Starting client sharding..." }
    val connectionProps = SpringExtension.getSpringProps(
            system,
            ClientConnectionActor::class.java,
            postmaster)
    val connectionExtractor = ConnectionShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_CONNECTION, connectionProps, settings, connectionExtractor)

    LOG.info("Started the sharding actors.")
  }

  private fun registerSingeltons() {
    LOG.info { "Starting the bootstrap actor." }

    val system = context.system()

    val settings = ClusterSingletonManagerSettings.create(system)
    val globalInitProps = SpringExtension.getSpringProps(system, BootstrapActor::class.java)
    val clusterProbs = ClusterSingletonManager.props(globalInitProps, PoisonPill.getInstance(), settings)
    system.actorOf(clusterProbs, "bootstrap")

    LOG.info { "Started the bootstrap actor." }
  }

  companion object {
    const val NAME = "bestia"
  }
}
