package net.bestia.zoneserver.actor.chat

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.AbstractActorTest
import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.actor.routing.DynamicMessageRouterActor
import net.bestia.zoneserver.chat.ChatCommandService
import net.bestia.zoneserver.entity.PlayerEntityService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Duration

class PublicChatActorTest : AbstractActorTest() {
  // Make a shared mocked bean ctx for faster testing.
  @MockBean
  lateinit var messageApi: MessageApi

  @MockBean
  lateinit var playerEntityService: PlayerEntityService

  @Test
  fun `test`() {
    testKit {
      val chat = actorOf(PublicChatActor::class)

      val chatMessage = ChatMessage(1, ChatMessage.Mode.PUBLIC, "Hello World")
      chat.tell(chatMessage, it.ref)

      it.awaitAssert(10.seconds, 100.ms) {
        verify(messageApi).send(any())
      }
    }
  }
}

private val Number.ms: Duration
  get() = Duration.ofSeconds(this.toLong())

private val Number.seconds: Duration
  get() = Duration.ofSeconds(this.toLong())
