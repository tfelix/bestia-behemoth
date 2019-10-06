package net.bestia.zoneserver.account

import akka.actor.ActorSystem
import net.bestia.zoneserver.TestZoneConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [TestZoneConfiguration::class])
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("actor")
abstract class AbstractIT {

  @Autowired
  protected lateinit var appCtx: ApplicationContext
  protected lateinit var system: ActorSystem
}