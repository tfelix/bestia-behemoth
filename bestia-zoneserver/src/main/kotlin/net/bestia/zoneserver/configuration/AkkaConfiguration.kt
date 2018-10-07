package net.bestia.zoneserver.configuration

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.Http
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging
import net.bestia.zoneserver.EntryActorNames
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.BestiaRootActor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.connection.ClientConnectionActor
import net.bestia.zoneserver.actor.connection.ConnectionShardMessageExtractor
import net.bestia.zoneserver.actor.connection.WebSocketRouter
import net.bestia.zoneserver.actor.entity.EntityActor
import net.bestia.zoneserver.actor.entity.EntityShardMessageExtractor
import net.bestia.zoneserver.actor.routing.RoutingActor
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
  fun actorSystem(appContext: ApplicationContext): ActorSystem {

    val akkaConfig = ConfigFactory.load(AKKA_CONFIG_NAME)
    LOG.debug { "Loaded akka config: $AKKA_CONFIG_NAME" }

    val system = ActorSystem.create("behemoth-local", akkaConfig)

    setupClusterDiscovery(system)

    SpringExtension.initialize(system, appContext)

    LOG.info { "Starting entity sharding..." }
    val settings = ClusterShardingSettings.create(system)
    val sharding = ClusterSharding.get(system)

    val entityProps = SpringExtension.getSpringProps(system, EntityActor::class.java)
    val entityExtractor = EntityShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_ENTITY, entityProps, settings, entityExtractor)
    LOG.info { "Started entity sharding" }

    LOG.info { "Starting client sharding..." }
    val connectionProps = SpringExtension.getSpringProps(system, ClientConnectionActor::class.java)
    val connectionExtractor = ConnectionShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_CONNECTION, connectionProps, settings, connectionExtractor)
    LOG.info("Started the sharding actors")

    LOG.info { "Starting websocket ingress..." }
    val materializer = ActorMaterializer.create(system)
    val http = Http.get(system)
    val router = WebSocketRouter(system)
    val routeFlow = router.createRoute().flow(system, materializer)
    http.bindAndHandle(routeFlow, ConnectHttp.toHost("localhost", 8090), materializer)
    LOG.info { "Started websocket ingress" }

    return system
  }

  private fun setupClusterDiscovery(system: ActorSystem) {
    // Akka Management hosts the HTTP routes used by bootstrap
    AkkaManagement.get(system).start()
    // Starting the bootstrap process needs to be done explicitly
    ClusterBootstrap.get(system).start()
  }

  @Bean
  @Qualifier("router")
  fun routerActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, RoutingActor::class.java)
  }

  @Bean
  fun rootActor(system: ActorSystem): ActorRef {
    LOG.info("Starting bestia root actor")
    return SpringExtension.actorOf(system, BestiaRootActor::class.java)
  }

  companion object {
    private const val AKKA_CONFIG_NAME = "akka"
  }
}
