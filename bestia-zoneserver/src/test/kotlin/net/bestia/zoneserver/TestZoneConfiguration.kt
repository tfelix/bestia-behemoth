package net.bestia.zoneserver

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import net.bestia.zoneserver.account.LoginCheck
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.routing.SystemMessageService
import net.bestia.zoneserver.config.RuntimeConfigService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Profile

/**
 * Configures the app context for testing operations.
 */
@TestConfiguration
@Profile("test")
@ComponentScan("net.bestia.zoneserver.actor")
class TestZoneConfiguration {

  // WTF? Why is this class totally ignored if there are no bean definitions in here?
  // Seems if Bean is not here the ComponentScan annotation is ignored.
  @Bean
  fun actorSystem(appCtx: ApplicationContext): ActorSystem {
    val akkaConfig = ConfigFactory.load("akka-test")
    val system = ActorSystem.create("testSystem", akkaConfig)

    // SpringExtension.initialize(system, appCtx, MockActorProducer::class.java)

    return system
  }

  @Bean
  fun allAuthenticatingLoginService(): LoginCheck {
    return AllAuthenticatingLoginService()
  }

  @MockBean
  lateinit var messageApi: MessageApi

  @MockBean
  lateinit var systemMessageService: SystemMessageService

  @MockBean
  lateinit var runtimeConfigService: RuntimeConfigService
}
