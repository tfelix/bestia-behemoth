package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendClientsInRangeActor
import net.bestia.zoneserver.actor.client.SendInRange
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.entity.PlayerEntityService

/**
 * Handles public chat of the user and sends them to all entities which can
 * receive them.
 *
 * @author Thomas Felix
 */
@Actor
class PublicChatActor(
    private val playerEntityService: PlayerEntityService,
    private val messageApi: MessageApi
) : AbstractActor() {

  private val sendActiveRange = SpringExtension.actorOf(context, SendClientsInRangeActor::class.java)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(ChatMessage::class.java, this::handlePublic)
        .build()
  }

  /**
   * Sends a public message to all clients in sight.
   */
  private fun handlePublic(chatMsg: ChatMessage) {
    val accId = chatMsg.accountId
    val activeEntityId = playerEntityService.getActivePlayerEntityId(accId) ?: return

    awaitEntityResponse(messageApi, context, activeEntityId) {
      val sendInRange = SendInRange(it, chatMsg)

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
