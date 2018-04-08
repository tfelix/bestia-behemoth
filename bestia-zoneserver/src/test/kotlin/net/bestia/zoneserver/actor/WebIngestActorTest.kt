package net.bestia.zoneserver.actor

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import net.bestia.messages.ClientFromMessageEnvelope
import net.bestia.zoneserver.TestZoneConfiguration
import net.bestia.zoneserver.actor.zone.WebIngestActor
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

  @Autowired
  lateinit var system: ActorSystem

  @Test
  fun testForward() {
    object : TestKit(system) {
      init {
        val postmasterTK = TestKit(system)
        val ingest = SpringExtension.actorOf(system, WebIngestActor::class.java, postmasterTK.ref)

        val msg = ClientFromMessageEnvelope("Test")
        ingest.tell(msg, ActorRef.noSender())
        postmasterTK.expectMsgEquals(duration("1 second"), msg)
      }
    }
  }
}