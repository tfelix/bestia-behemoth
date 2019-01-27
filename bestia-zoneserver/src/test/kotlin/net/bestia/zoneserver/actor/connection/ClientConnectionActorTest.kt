package net.bestia.zoneserver.actor.connection

import akka.actor.ActorRef
import akka.testkit.javadsl.TestKit
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import net.bestia.messages.client.ClientConnectMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.account.LoginService
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.SpringExtension
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean

class ClientConnectionActorTest : AbstractActorTest() {
  @MockBean
  lateinit var loginService: LoginService

  @Test
  fun testClientConnectionHandshake() {
    testKit {
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