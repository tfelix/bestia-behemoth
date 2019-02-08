package net.bestia.zoneserver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import net.bestia.zoneserver.actor.MockActorProducer
import net.bestia.zoneserver.actor.SpringExtension
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Configures the app context for testing operations.
 */
@Configuration
@Profile("test")
@ComponentScan("net.bestia.zoneserver.actor")
class TestZoneConfiguration {

  // WTF? Why is this class totally ignored if there are no bean definitions in here?
  // Seems if Bean is not here the ComponentScan annotation is ignored.
  @Bean
  fun actorSystem(appCtx: ApplicationContext): ActorSystem {
    val akkaConfig = ConfigFactory.load("akka-test")
    val system = ActorSystem.create("testSystem", akkaConfig)

    SpringExtension.initialize(system, appCtx, MockActorProducer::class.java)

    return system
  }
}
