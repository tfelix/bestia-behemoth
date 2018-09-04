package net.bestia.zoneserver.actor.connection

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.verify
import net.bestia.messages.client.ClientConnectMessage
import net.bestia.messages.client.ToClientEnvelope
import net.bestia.zoneserver.TestZoneConfiguration
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.client.LoginService
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
        val toClient = ToClientEnvelope(10, content)
        ingest.tell(toClient, ActorRef.noSender())
        socket.expectMsg(content)
      }
    }
  }
}