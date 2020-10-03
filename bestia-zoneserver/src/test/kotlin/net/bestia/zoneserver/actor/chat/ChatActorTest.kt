package net.bestia.zoneserver.actor.chat

import akka.actor.ActorRef
import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatResponse
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.chat.ChatCommandService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean

class ChatActorTest : AbstractActorTest() {

  @MockBean
  lateinit var chatCmdService: ChatCommandService

  @Test
  fun `routes messages to the sub actors by type`() {
    testKit {
      val chat = testActorOf(ChatActor::class)

      val probes = injectProbeMembers(chat, listOf(
          "publicChatActor",
          "whisperChatActor",
          "guildChatActor",
          "partyChatActor"
      ))

      val pubChat = ChatResponse(1, ChatMode.PUBLIC, "Hello PUBLIC")
      chat.tell(pubChat, ActorRef.noSender())
      val chatMessage = ChatResponse(1, ChatMode.SYSTEM, "Hello SYSTEM")
      chat.tell(chatMessage, ActorRef.noSender())
      val guildMessage = ChatResponse(1, ChatMode.GUILD, "Hello GUILD")
      chat.tell(guildMessage, ActorRef.noSender())
      val partyMessage = ChatResponse(1, ChatMode.PARTY, "Hello PARTY")
      chat.tell(partyMessage, ActorRef.noSender())

      probes["publicChatActor"]!!.expectMsg(pubChat)
      probes["guildChatActor"]!!.expectMsg(guildMessage)
      probes["partyChatActor"]!!.expectMsg(partyMessage)
    }
  }
}