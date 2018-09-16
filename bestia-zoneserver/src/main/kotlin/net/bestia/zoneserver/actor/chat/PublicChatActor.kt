package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendClientsInRangeActor
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Handles public chat of the user and sends them to all entities which can
 * receive them.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class PublicChatActor(
        private val playerEntityService: PlayerEntityService,
        private val entityService: EntityService
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
    if (chatMsg.chatMode != ChatMessage.Mode.PUBLIC) {
      LOG.warn { "Can not handle non public chat messages: $chatMsg." }
      unhandled(chatMsg)
      return
    }

    val accId = chatMsg.accountId
    val pbe = playerEntityService.getActivePlayerEntity(accId) ?: return
    // Add the current entity id to the message.
    val chatEntityMsg = ChatMessage(accId, pbe.id, chatMsg)

    val pos = entityService.getComponent(pbe, PositionComponent::class.java)

    if (!pos.isPresent) {
      LOG.warn { "Player bestia has no position component." }
      return
    }

    // We dont need to send a echo back because the player entity is also
    // active in the area so this call also includes the sender of the chat
    // message.
    sendActiveRange.tell(chatEntityMsg, self)
  }

  companion object {
    const val NAME = "public"
  }
}
