package net.bestia.zoneserver.actor

import akka.actor.Props
import akka.actor.ActorSystem
import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.testkit.javadsl.TestKit
import net.bestia.zoneserver.actor.entity.EntityActor
import org.junit.Assert
import org.junit.jupiter.api.*
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.junit4.SpringRunner
import java.time.Duration


@RunWith(SpringRunner::class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ActorBaseTest {
  protected lateinit var system: ActorSystem

  @Autowired
  lateinit var applicationContext: ApplicationContext

  @BeforeEach
  fun test() {
    SpringExtension.initialize(system, applicationContext)
  }

  @BeforeAll
  fun beforeClass() {
    system = ActorSystem.create()
  }

  @AfterAll
  fun afterClass() {
    TestKit.shutdownActorSystem(system)
  }

  protected fun testKit(fn: (TestKit) -> Unit) {
    object : TestKit(system) {
      init {
        fn(this)
      }
    }
  }
}

@SpringBootTest
class EntityActorTest : ActorBaseTest() {

  @Test
  fun testIt() {
    testKit {
      val subject = SpringExtension.actorOf(system, EntityActor::class.java)

      // can also use JavaTestKit “from the outside”
      val probe = TestKit(system)
      // “inject” the probe by passing it to the test subject
      // like a real resource would be passed in production
      subject.tell(probe.ref, it.ref)
      // await the correct response
      it.expectMsg(Duration.ofSeconds(1), "done")

      // the run() method needs to finish within 3 seconds
      it.within(Duration.ofSeconds(3)) {
        subject.tell("hello", it.ref)

        // This is a demo: would normally use expectMsgEquals().
        // Wait time is bounded by 3-second deadline above.
        it.awaitCond { probe.msgAvailable() }

        // response must have been enqueued to us before probe
        it.expectMsg(Duration.ZERO, "world")
        // check that the probe we injected earlier got the msg
        probe.expectMsg(Duration.ZERO, "hello")
        Assert.assertEquals(it.ref, probe.getLastSender())

        // Will wait for the rest of the 3 seconds
        it.expectNoMessage()
      }
    }
  }
}