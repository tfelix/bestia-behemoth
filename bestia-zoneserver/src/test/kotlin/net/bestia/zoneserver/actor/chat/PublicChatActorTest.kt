package net.bestia.zoneserver.actor.chat

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatResponse
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.client.SendInRange
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.PlayerEntityService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean

class PublicChatActorTest : AbstractActorTest() {

  @MockBean
  lateinit var playerEntityService: PlayerEntityService

  @Test
  fun `public messages are sent to players near by`() {
    val activeEntityId = 1L
    val entity = Entity(id = activeEntityId)
    val chatMessage = ChatResponse(1, ChatMode.PUBLIC, "Hello World")
    whenever(playerEntityService.getActivePlayerEntityId(any())).thenReturn(activeEntityId)

    testKit {
      val chat = testActorOf(PublicChatActor::class)
      val probes = injectProbeMembers(chat, listOf("sendActiveRange"))

      whenAskedForEntity(activeEntityId, entity)

      chat.tell(chatMessage, it.ref)

      probes["sendActiveRange"]!!.expectMsg(SendInRange(entity, chatMessage))

      it.awaitAssert {
        verify(messageApi).send(any<EntityEnvelope>())
      }
    }
  }
}
