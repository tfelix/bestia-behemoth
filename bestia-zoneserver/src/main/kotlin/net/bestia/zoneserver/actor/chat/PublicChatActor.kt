package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import net.bestia.messages.chat.ChatMessage
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.messages.entity.EntityRequest
import net.bestia.messages.entity.EntityResponse
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendClientsInRangeActor
import net.bestia.zoneserver.actor.client.SendInRange
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * Handles public chat of the user and sends them to all entities which can
 * receive them.
 *
 * @author Thomas Felix
 */
@ActorComponent
class PublicChatActor(
    private val playerEntityService: PlayerEntityService,
    private val messageApi: MessageApi
) : AbstractActor() {

  private val sendActiveRange = SpringExtension.actorOf(context, SendClientsInRangeActor::class.java)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(ChatMessage::class.java, this::handlePublic)
        .match(EntityResponse::class.java, this::handleEntityResponse)
        .build()
  }

  private fun handleEntityResponse(response: EntityResponse) {
    val sendInRange = SendInRange(
        response.entity,
        response.content as ChatMessage
    )

    // We dont need to send a echo back because the player entity is also
    // active in the area so this call also includes the sender of the chat
    // message.
    sendActiveRange.tell(sendInRange, self)
  }

  /**
   * Sends a public message to all clients in sight.
   */
  private fun handlePublic(chatMsg: ChatMessage) {
    val accId = chatMsg.accountId
    val activeEntityId = playerEntityService.getActivePlayerEntityId(accId) ?: return
    val chatEntityMsg = chatMsg.copy(entityId = activeEntityId)

    val requestEntity = EntityRequest(self, chatEntityMsg)
    messageApi.send(EntityEnvelope(activeEntityId, requestEntity))
  }

  companion object {
    const val NAME = "public"
  }
}
