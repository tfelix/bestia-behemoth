package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.BestiaRepository
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.zoneserver.TestZoneConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ComponentScan
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import kotlin.reflect.KClass

@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension::class)
@ContextConfiguration(classes = [TestZoneConfiguration::class])
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("actor")
abstract class AbstractActorTest {

  @MockBean
  protected lateinit var accountRepository: AccountRepository

  @MockBean
  protected lateinit var playerBestiaRepository: PlayerBestiaRepository

  @MockBean
  protected  lateinit var bestiaRepository: BestiaRepository

  @Autowired
  protected lateinit var appCtx: ApplicationContext
  protected lateinit var system: ActorSystem

  @BeforeAll
  fun initialize() {
    system = ActorSystem.create()
    SpringExtension.initialize(system, appCtx, MockActorProducer::class.java)
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