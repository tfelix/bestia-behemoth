package net.bestia.zoneserver.actor.connection

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import net.bestia.messages.client.ClientConnectMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.TestZoneConfiguration
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.account.LoginService
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner

@SpringBootTest
@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@Import(TestZoneConfiguration::class)
class IngestActorTest {

  @Autowired
  lateinit var system: ActorSystem

  @MockBean
  lateinit var loginService: LoginService

  @Test
  fun testClientConnectionHandshake() {
    object : TestKit(system) {
      init {
        val socket = TestKit(system)
        val ingest = SpringExtension.actorOf(system, ClientConnectionActor::class.java)

        val conMesg = ClientConnectMessage(
                accountId = 10,
                webserverRef = socket.ref
        )

        ingest.tell(conMesg, socket.ref)

        verify(loginService.login(eq(10)))

        val content = "Hello World"
        val toClient = ClientEnvelope(10, content)
        ingest.tell(toClient, ActorRef.noSender())
        socket.expectMsg(content)
      }
    }
  }
}