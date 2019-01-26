package net.bestia.zoneserver.actor.chat

import akka.testkit.javadsl.TestKit
import com.nhaarman.mockitokotlin2.verify
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.DynamicMessageRouterActor
import net.bestia.zoneserver.chat.ChatCommandService
import org.junit.Test
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Duration

class ChatActorTest : AbstractActorTest() {

  @MockBean
  lateinit var chatCmdService: ChatCommandService

  @Test
  fun `calls chat service to execute chat functions`() {
    object : TestKit(system) {
      init {

        val chat = SpringExtension.actorOf(system, ChatActor::class.java)
        val chatMessage = ChatMessage(1, ChatMessage.Mode.SYSTEM, "Hello World")
        chat.tell(chatMessage, ref)

        expectMsgClass(DynamicMessageRouterActor.RedirectMessage::class.java)
        awaitAssert(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
          verify(chatCmdService).isChatCommand("Hello World")
        }
      }
    }
  }
}