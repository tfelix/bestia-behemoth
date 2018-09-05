package net.bestia.zoneserver.actor.chat

import akka.testkit.javadsl.TestKit
import com.nhaarman.mockito_kotlin.verify
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.BaseActorTest
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.actor.seconds
import net.bestia.zoneserver.actor.toScala
import net.bestia.zoneserver.chat.ChatCommandService
import org.junit.Test
import org.springframework.boot.test.mock.mockito.MockBean

class ChatActorTest : BaseActorTest() {

  @MockBean
  lateinit var chatCmdService: ChatCommandService

  @Test
  fun `calls chat service to execute chat functions`() {
    object : TestKit(system) {
      init {

        val chat = SpringExtension.actorOf(system, ChatActor::class.java)
        val chatMessage = ChatMessage(1, 1, "Hello World", ChatMessage.Mode.PUBLIC)
        chat.tell(chatMessage, ref)

        expectMsgClass(BaseClientMessageRouteActor.RedirectMessage::class.java)
        awaitAssert(1.seconds().toScala(), 1.seconds().toScala(),
                { verify(chatCmdService).isChatCommand("Hello World") }
        )
      }
    }
  }
}