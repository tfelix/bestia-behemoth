package net.bestia.zoneserver.actor

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import akka.testkit.javadsl.TestKit
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.zoneserver.actor.entity.EntityRequest
import net.bestia.zoneserver.actor.entity.EntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.integration.TestZoneConfiguration
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

/**
 * The createMockedActors flag determines if real actors are created inside the tested actores or if only mocks
 * should be build. Mocks might be better if the internal messaging should be tested.
 */
@ExtendWith(SpringJunitExtension::class)
@ContextConfiguration(classes = [TestZoneConfiguration::class])
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("actor")
@SpringBootTest
abstract class AbstractActorTest(
    private val createMockedActors: Boolean = true
) {

  protected class InjectedProbes(
      private val probes: Map<String, TestProbe>
  ) {

    operator fun get(key: String): TestProbe {
      return probes[key] ?: error("Probe $key was not injected")
    }
  }

  @Autowired
  protected lateinit var appCtx: ApplicationContext

  @Autowired
  lateinit var messageApi: MessageApi

  protected lateinit var system: ActorSystem

  @BeforeAll
  fun initialize() {
    system = ActorSystem.create()

    if (createMockedActors) {
      SpringExtension.initialize(system, appCtx, MockActorProducer::class.java)
      SpringNoMockExtension.initialize(system, appCtx)
    } else {
      SpringExtension.initialize(system, appCtx, SpringActorProducer::class.java)
    }
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

  protected fun whenAskedForEntity(entityId: Long, responseEntity: Entity) {
    whenever(messageApi.send(isA<EntityRequest>())).doAnswer {
      val request = it.getArgument(0) as EntityRequest
      val response = EntityResponse(responseEntity)
      request.replyTo.tell(response, ActorRef.noSender())
    }
  }

  /**
   * Replaces fields inside the actor with probes.
   */
  protected fun <T : AbstractActor> injectProbeMembers(
      actor: TestActorRef<T>,
      probes: List<String>
  ): InjectedProbes {
    val rawActor = actor.underlyingActor()

    val mappedProbed = probes.map { it to TestProbe(system) }.toMap()

    mappedProbed.forEach { (fieldName, probe) ->
      val field = rawActor.javaClass.getDeclaredField(fieldName)
      field.isAccessible = true
      field.set(rawActor, probe.ref())
    }

    return InjectedProbes(mappedProbed)
  }

  protected fun <T : AbstractActor> testActorOf(actorClass: KClass<T>, vararg args: Any): TestActorRef<T> {
    val props = if (createMockedActors) {
      SpringNoMockExtension.getSpringProps(system, actorClass.java, *args)
    } else {
      SpringExtension.getSpringProps(system, actorClass.java, *args)
    }

    return TestActorRef.create<T>(system, props)
  }
}