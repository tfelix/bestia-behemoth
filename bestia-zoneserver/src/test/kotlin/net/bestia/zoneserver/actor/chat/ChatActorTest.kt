package net.bestia.zoneserver.actor.chat

import com.nhaarman.mockitokotlin2.verify
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.routing.DynamicMessageRouterActor
import net.bestia.zoneserver.chat.ChatCommandService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Duration

class ChatActorTest : AbstractActorTest() {

  @MockBean
  lateinit var chatCmdService: ChatCommandService

  @Test
  fun `calls chat service to execute chat functions`() {
    testKit {
      val chat = actorOf(ChatActor::class)

      val chatMessage = ChatMessage(1, ChatMessage.Mode.SYSTEM, "Hello World")
      chat.tell(chatMessage, it.ref)

      it.expectMsgClass(DynamicMessageRouterActor.RedirectMessage::class.java)
      it.awaitAssert(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
        verify(chatCmdService).isChatCommand("Hello World")
      }
    }
  }
}