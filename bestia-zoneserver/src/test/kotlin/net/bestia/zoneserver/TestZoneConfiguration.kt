package net.bestia.zoneserver

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.bestia.zoneserver.actor.SpringExtension
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Scope
import org.springframework.test.context.ActiveProfiles

/**
 * Configures the app context for testing operations.
 *
 * @author Thomas Felix
 */
@TestConfiguration
@ActiveProfiles("test")
class TestZoneConfiguration {

  @Bean
  @Scope("prototype")
  @Primary
  fun actorSystem(appCtx: ApplicationContext): ActorSystem {
    val akkaConfig = ConfigFactory.load("akka-test")
    val system = ActorSystem.create("testSystem", akkaConfig)
    SpringExtension.initialize(system, appCtx)
    return system
  }
}
