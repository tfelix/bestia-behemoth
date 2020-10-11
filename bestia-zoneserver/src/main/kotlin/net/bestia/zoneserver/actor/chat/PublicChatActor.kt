package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import net.bestia.messages.chat.ChatRequest
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendClientsInRangeActor
import net.bestia.zoneserver.actor.client.SendInRange
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.chat.PublicChatService
import java.lang.IllegalStateException

/**
 * Handles public chat of the user and sends them to all entities which can
 * receive them.
 *
 * @author Thomas Felix
 */
@Actor
class PublicChatActor(
    private val publicChatService: PublicChatService,
    private val messageApi: MessageApi
) : AbstractActor() {

  private val sendActiveRange = SpringExtension.actorOf(context, SendClientsInRangeActor::class.java)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(ChatRequest::class.java, this::handlePublic)
        .build()
  }

  /**
   * Sends a public message to all clients in sight.
   */
  private fun handlePublic(chat: ChatRequest) {
    val response = publicChatService.getChatResponse(chat)
    val entityId = response.entityId ?: throw IllegalStateException("Player has not active Bestia")

    awaitEntityResponse(messageApi, context, entityId) {
      val sendInRange = SendInRange(it, response)

      // We dont need to send a echo back because the player entity is also
      // active in the area so this call also includes the sender of the chat
      // message.
      sendActiveRange.tell(sendInRange, self)
    }
  }

  companion object {
    const val NAME = "public"
  }
}
