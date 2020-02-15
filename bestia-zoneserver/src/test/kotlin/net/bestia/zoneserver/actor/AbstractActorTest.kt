package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import akka.testkit.javadsl.TestKit
import net.bestia.zoneserver.TestZoneConfiguration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import kotlin.reflect.KClass
import org.springframework.test.context.junit.jupiter.SpringExtension as SpringJunitExtension

@ExtendWith(SpringJunitExtension::class)
@ContextConfiguration(classes = [TestZoneConfiguration::class])
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("actor")
@SpringBootTest
abstract class AbstractActorTest {

  @Autowired
  protected lateinit var appCtx: ApplicationContext
  protected lateinit var system: ActorSystem

  @BeforeAll
  fun initialize() {
    system = ActorSystem.create()
    SpringExtension.initialize(system, appCtx, MockActorProducer::class.java)
    SpringNoMockExtension.initialize(system, appCtx)
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

  /**
   * Replaces fields inside the actor with probes.
   */
  protected fun <T : AbstractActor> injectProbeMembers(
      actor: TestActorRef<T>,
      probes: List<String>
  ): Map<String, TestProbe> {
    val rawActor = actor.underlyingActor()

    val mappedProbed = probes.map { it to TestProbe(system) }.toMap()

    mappedProbed.forEach { (fieldName, probe) ->
      val field = rawActor.javaClass.getDeclaredField(fieldName)
      field.isAccessible = true
      field.set(rawActor, probe.ref())
    }

    return mappedProbed
  }

  protected fun <T : AbstractActor> testActorOf(actorClass: KClass<T>, vararg args: Any): TestActorRef<T> {
    val props = SpringNoMockExtension.getSpringProps(system, actorClass.java, args)

    return TestActorRef.create<T>(system, props)
  }
}