package net.bestia.zoneserver.config

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.TypedActor
import akka.actor.TypedProps
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging
import net.bestia.zoneserver.AkkaMessageApi
import net.bestia.zoneserver.EntryActorNames
import net.bestia.zoneserver.actor.BestiaRootActor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.connection.ClientConnectionActor
import net.bestia.zoneserver.actor.connection.ConnectionShardMessageExtractor
import net.bestia.zoneserver.actor.entity.EntityActor
import net.bestia.zoneserver.actor.entity.EntityShardMessageExtractor
import net.bestia.zoneserver.actor.routing.RoutingActor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
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

    // TEST
    LOG.info { "Configuring sharding actors." }

    LOG.info { "Starting entity sharding..." }
    val settings = ClusterShardingSettings.create(system)
    val sharding = ClusterSharding.get(system)

    val entityProps = SpringExtension.getSpringProps(system, EntityActor::class.java)
    val entityExtractor = EntityShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_ENTITY, entityProps, settings, entityExtractor)

    LOG.info { "Starting client sharding..." }
    val connectionProps = SpringExtension.getSpringProps(system, ClientConnectionActor::class.java)
    val connectionExtractor = ConnectionShardMessageExtractor()
    sharding.start(EntryActorNames.SHARD_CONNECTION, connectionProps, settings, connectionExtractor)

    LOG.info("Started the sharding actors.")

    return system
  }

  private fun setupClusterDiscovery(system: ActorSystem) {
    // Akka Management hosts the HTTP routes used by bootstrap
    AkkaManagement.get(system).start()
    // Starting the bootstrap process needs to be done explicitly
    ClusterBootstrap.get(system).start()
  }

  @Bean
  @Primary
  fun messageApi(system: ActorSystem): MessageApi {
    val typedProps = TypedProps(MessageApi::class.java, AkkaMessageApi::class.java)
    return TypedActor.get(system).typedActorOf(typedProps, "akkaMsgApi")
  }

  @Bean
  @Qualifier("router")
  fun routerActor(system: ActorSystem): ActorRef {
    return SpringExtension.actorOf(system, RoutingActor::class.java)
  }

  @Bean
  fun rootActor(msgApi: MessageApi, system: ActorSystem): ActorRef {
    LOG.info("Starting bestia root actor")
    return SpringExtension.actorOf(system, BestiaRootActor::class.java, msgApi)
  }

  companion object {
    private const val AKKA_CONFIG_NAME = "akka"
  }
}
