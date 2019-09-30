package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.cluster.singleton.ClusterSingletonProxy
import akka.http.javadsl.ConnectHttp
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging
import net.bestia.zoneserver.ShardActorNames
import net.bestia.zoneserver.actor.bootstrap.ClusterBootstrapActor
import net.bestia.zoneserver.actor.client.ClientMessageRoutingActor
import net.bestia.zoneserver.actor.entity.EntityActor
import net.bestia.zoneserver.actor.entity.EntityShardMessageExtractor
import net.bestia.zoneserver.actor.routing.EntityRoutingActor
import net.bestia.zoneserver.actor.routing.SystemRoutingActor
import net.bestia.zoneserver.config.ZoneserverNodeConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.UnknownHostException
import akka.cluster.singleton.ClusterSingletonProxySettings
import net.bestia.zoneserver.actor.client.ClusterClientConnectionManagerActor

private val LOG = KotlinLogging.logger { }

/**
 * Generates the akka configuration file which is used to connect to the remote
 * actor system of the bestia zone server. It overwrites the default akka config
 * file "application.conf" since this is already used by spring with
 * "akka.config".
 *
 * @author Thomas Felix
 */
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

    setupClusterDiscovery(system)
    SpringExtension.initialize(system, appContext)
    setupSharding(system)

    val clientMessageRouting = SpringExtension.actorOf(system, ClientMessageRoutingActor::class.java)
    // scriptService.callScriptMainFunction("startup")

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
  }

  private fun setupSingeltons(system: ActorSystem) {
    LOG.info { "Starting the bootstrap actor" }
    val settings = ClusterSingletonManagerSettings.create(system)
    startAsSingelton(system, settings, ClusterBootstrapActor::class.java, "bootstrap")
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
  @Qualifier(SYSTEM_ROUTER_QUALIFIER)
  fun systemRouterActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, SystemRoutingActor::class.java)
  }

  @Bean
  @Qualifier(ENTITY_ROUTER_QUALIFIER)
  fun entityRouterActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, EntityRoutingActor::class.java)
  }

  @Bean
  @Qualifier(CONNECTION_MANAGER)
  fun connectionManager(system: ActorSystem): ActorRef {
    val proxySettings = ClusterSingletonProxySettings.create(system)
    val props = ClusterSingletonProxy.props("/user/${ClusterClientConnectionManagerActor.NAME}", proxySettings)

    return system.actorOf(props, ClusterClientConnectionManagerActor.NAME)
  }

  companion object {
    private const val AKKA_CONFIG_NAME = "akka"

    const val ENTITY_ROUTER_QUALIFIER = "entityRouter"
    const val SYSTEM_ROUTER_QUALIFIER = "systemRouter"
    const val CONNECTION_MANAGER = "clientConnectionManager"
  }
}
