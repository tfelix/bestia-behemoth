package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging
import net.bestia.zoneserver.ShardActorNames
import net.bestia.zoneserver.actor.BQualifier.CLIENT_FORWARDER
import net.bestia.zoneserver.actor.BQualifier.CLIENT_MESSAGE_ROUTER
import net.bestia.zoneserver.actor.BQualifier.ENTITY_FORWARDER
import net.bestia.zoneserver.actor.BQualifier.RUNTIME_CONFIG
import net.bestia.zoneserver.actor.BQualifier.SYSTEM_ROUTER
import net.bestia.zoneserver.actor.bootstrap.ClusterBootstrapActor
import net.bestia.zoneserver.actor.bootstrap.ClusterMonitorActor
import net.bestia.zoneserver.actor.bootstrap.NodeBootstrapActor
import net.bestia.zoneserver.actor.client.ClientMessageRoutingActor
import net.bestia.zoneserver.actor.client.ClusterClientConnectionManagerActor
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.config.RuntimeConfigurationActor
import net.bestia.zoneserver.actor.entity.EntityActor
import net.bestia.zoneserver.actor.entity.EntityShardMessageExtractor
import net.bestia.zoneserver.actor.entity.SendToEntityActor
import net.bestia.zoneserver.actor.routing.SystemRoutingActor
import net.bestia.zoneserver.actor.socket.SocketServerActor
import net.bestia.zoneserver.config.ZoneserverNodeConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.net.UnknownHostException

private val LOG = KotlinLogging.logger { }

/**
 * Generates the akka configuration file which is used to connect to the remote
 * actor system of the bestia zone server. It overwrites the default akka config
 * file "application.conf" since this is already used by spring with
 * "akka.config".
 *
 * @author Thomas Felix
 */
@Profile("!test")
@Configuration
class AkkaConfiguration {

  @Bean
  @Throws(UnknownHostException::class)
  fun actorSystem(
      appContext: ApplicationContext,
      zoneConfig: ZoneserverNodeConfig
  ): ActorSystem {
    val akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME)
    LOG.debug { "Loaded akka config: $AKKA_CONFIG_NAME" }

    LOG.info { "Starting Behemoth actor system" }
    val system = ActorSystem.create("behemoth-local", akkaConfig)

    SpringExtension.initialize(system, appContext)
    setupClusterDiscovery(system)

    setupSharding(system)
    setupClusterSingeltons(system)

    SpringExtension.actorOf(system, NodeBootstrapActor::class.java)
    SpringExtension.actorOf(system, SocketServerActor::class.java)

    return system
  }

  private fun setupSharding(system: ActorSystem) {
    val settings = ClusterShardingSettings.create(system)
    val sharding = ClusterSharding.get(system)

    LOG.info { "Starting entity sharding..." }
    val entityProps = SpringExtension.getSpringProps(system, EntityActor::class.java)
    val entityExtractor = EntityShardMessageExtractor()
    sharding.start(ShardActorNames.SHARD_ENTITY, entityProps, settings, entityExtractor)
  }

  private fun setupClusterDiscovery(system: ActorSystem) {
    AkkaManagement.get(system).start()
    ClusterBootstrap.get(system).start()
    SpringExtension.actorOf(system, ClusterMonitorActor::class.java)
  }

  private fun setupClusterSingeltons(system: ActorSystem) {
    LOG.info { "Starting the bootstrap actor" }
    val settings = ClusterSingletonManagerSettings.create(system)
    startAsSingelton(system, settings, ClusterBootstrapActor::class.java, "clusterBootstrap")

    LOG.info { "Starting the client connection manager actor" }
    startAsSingelton(system, settings, ClusterClientConnectionManagerActor::class.java, ClusterClientConnectionManagerActor.NAME)
  }

  private fun <T : AbstractActor> startAsSingelton(
      system: ActorSystem,
      settings: ClusterSingletonManagerSettings,
      actorClass: Class<T>,
      name: String
  ) {
    val props = SpringExtension.getSpringProps(system, actorClass)
    val clusterProbs = ClusterSingletonManager.props(props, PoisonPill.getInstance(), settings)
    system.actorOf(clusterProbs, name)
  }

  @Bean
  @Qualifier(CLIENT_MESSAGE_ROUTER)
  fun clientMessageRouterActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, ClientMessageRoutingActor::class.java)
  }

  @Bean
  @Qualifier(SYSTEM_ROUTER)
  fun systemRouterActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, SystemRoutingActor::class.java)
  }

  /**
   * Sends messages specific to entity actors.I
   */
  @Bean
  @Qualifier(ENTITY_FORWARDER)
  fun entityRouterActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, SendToEntityActor::class.java)
  }

  /**
   * Sends messages to a connected client.
   */
  @Bean
  @Qualifier(CLIENT_FORWARDER)
  fun clientRouterActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, SendToClientActor::class.java)
  }

  /**
   * Enables access and interaction with the runtime configuration.
   * The runtime config is replicated between the cluster.
   */
  @Bean
  @Qualifier(RUNTIME_CONFIG)
  fun runtimeConfigActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, RuntimeConfigurationActor::class.java)
  }

  companion object {
    private const val AKKA_CONFIG_NAME = "akka"
  }
}