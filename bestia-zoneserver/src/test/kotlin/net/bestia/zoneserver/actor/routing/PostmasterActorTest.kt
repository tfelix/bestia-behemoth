package net.bestia.zoneserver.actor.routing

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.JavaTestKit.duration
import akka.testkit.javadsl.TestKit
import net.bestia.messages.Envelope
import net.bestia.zoneserver.TestZoneConfiguration
import net.bestia.zoneserver.actor.SpringExtension
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@SpringBootTest
@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@Import(TestZoneConfiguration::class)
class WebIngestActorTest {

  class TestEnvelope(content: String) : Envelope(content)

  val defaultDuration = duration("1 second")!!

  @Autowired
  lateinit var system: ActorSystem

  @Test
  fun registers_actors_for_receiving() {
    object : TestKit(system) {
      init {
        val recv1 = TestKit(system)
        val recv2 = TestKit(system)
        val sender = TestKit(system)
        val postmaster = SpringExtension.actorOf(system, PostmasterActor::class.java)

        val registerMsg1 = RegisterEnvelopeMessage(
                TestEnvelope::class.java,
                recv1.ref
        )
        val registerMsg2 = RegisterEnvelopeMessage(
                TestEnvelope::class.java,
                recv2.ref
        )

        postmaster.tell(registerMsg1, ActorRef.noSender())
        postmaster.tell(registerMsg2, ActorRef.noSender())

        val testContent = "Hello World"
        val testMsg = TestEnvelope(testContent)
        postmaster.tell(testMsg, sender.ref)
        recv1.expectMsg(defaultDuration, testContent)
        Assert.assertEquals(sender.ref, recv1.lastSender)
        recv2.expectMsg(defaultDuration, testContent)
        Assert.assertEquals(sender.ref, recv2.lastSender)
      }
    }
  }
}