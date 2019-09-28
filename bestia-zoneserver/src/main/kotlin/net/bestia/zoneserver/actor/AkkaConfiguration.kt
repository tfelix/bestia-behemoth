package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.javadsl.AkkaManagement
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging
import net.bestia.zoneserver.ShardActorNames
import net.bestia.zoneserver.actor.bootstrap.BootstrapActor
import net.bestia.zoneserver.actor.client.ClientMessageRoutingActor
import net.bestia.zoneserver.actor.connection.ClientConnectionActor
import net.bestia.zoneserver.actor.connection.ConnectionShardMessageExtractor
import net.bestia.zoneserver.actor.connection.WebSocketRouter
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

    val materializer = ActorMaterializer.create(system)
    val http = Http.get(system)
    val router = WebSocketRouter(system, clientMessageRouting)
    val routeFlow = router.createRoute().flow(system, materializer)
    val websocketConnect = ConnectHttp.toHost("localhost", zoneConfig.websocketPort)
    LOG.info { "Starting websocket ingress on $websocketConnect..." }
    http.bindAndHandle(routeFlow, websocketConnect, materializer)

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

    LOG.info { "Starting client sharding..." }
    val connectionProps = SpringExtension.getSpringProps(system, ClientConnectionActor::class.java)
    val connectionExtractor = ConnectionShardMessageExtractor()
    sharding.start(ShardActorNames.SHARD_CONNECTION, connectionProps, settings, connectionExtractor)
  }

  private fun setupClusterDiscovery(system: ActorSystem) {
    AkkaManagement.get(system).start()
    ClusterBootstrap.get(system).start()
  }

  private fun setupSingeltons(system: ActorSystem) {
    LOG.info { "Starting the bootstrap actor." }
    val settings = ClusterSingletonManagerSettings.create(system)
    startAsSingelton(system, settings, BootstrapActor::class.java, "bootstrap")
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

  companion object {
    private const val AKKA_CONFIG_NAME = "akka"
    const val ENTITY_ROUTER_QUALIFIER = "entityRouter"
    const val SYSTEM_ROUTER_QUALIFIER = "systemRouter"
  }
}
