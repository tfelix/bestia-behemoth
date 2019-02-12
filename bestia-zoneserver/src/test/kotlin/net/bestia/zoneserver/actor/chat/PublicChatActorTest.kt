package net.bestia.zoneserver.actor.chat

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.actor.client.SendInRange
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.EntityRequest
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.PlayerEntityService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean

class PublicChatActorTest : AbstractActorTest() {
  @MockBean
  lateinit var messageApi: MessageApi

  @MockBean
  lateinit var playerEntityService: PlayerEntityService

  @Test
  fun `public messages are sent to players near by`() {
    val entity = Entity(id = 10)
    val activeEntityId = 1L
    val chatMessage = ChatMessage(1, ChatMessage.Mode.PUBLIC, "Hello World")
    whenever(playerEntityService.getActivePlayerEntityId(any())).thenReturn(activeEntityId)

    testKit {
      val chat = testActorOf(PublicChatActor::class)
      val probes = injectProbeMembers(chat, listOf("sendActiveRange"))

      whenever(messageApi.send(any<EntityEnvelope>())).doAnswer {
        // val msg = it.getArgument<EntityEnvelope>(0)
      }

      chat.tell(chatMessage, it.ref)

      probes["sendActiveRange"]!!.expectMsg(SendInRange(entity, chatMessage))

      it.awaitAssert {
        verify(messageApi).send(any())
      }
    }
  }
}
