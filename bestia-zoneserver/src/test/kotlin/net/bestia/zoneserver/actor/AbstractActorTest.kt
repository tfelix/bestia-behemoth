package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import net.bestia.zoneserver.TestZoneConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.reflect.KClass

@SpringBootTest
@ActiveProfiles("test")
@Import(TestZoneConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension::class)
abstract class AbstractActorTest {

  @Autowired
  protected lateinit var appCtx: ApplicationContext
  protected lateinit var system: ActorSystem

  @BeforeAll
  fun initialize() {
    system = ActorSystem.create()
    SpringExtension.initialize(system, appCtx)
  }

  @AfterAll
  fun teardown() {
    TestKit.shutdownActorSystem(system)
  }

  protected fun testKit(fn: (TestKit) -> Unit) {
    object : TestKit(system) {
      init {
        fn(this)
      }
    }
  }

  protected fun <T : AbstractActor> actorOf(actorClass: KClass<T>): ActorRef {
    return SpringExtension.actorOf(system, actorClass.java)
  }
}