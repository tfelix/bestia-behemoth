package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.chat.GuildChatService
import net.bestia.zoneserver.entity.component.GuildComponent

private val LOG = KotlinLogging.logger { }

/**
 * Handles guild chats.
 * It sends the chat message to all online guild members.
 * If the user is no member of a guild it does nothing.
 *
 * @author Thomas Felix
 */
@Actor
class GuildChatActor(
    private val guildChatService: GuildChatService,
    private val messageApi: MessageApi
) : AbstractActor() {

  private val sendToClientActor = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ChatMessage::class.java, this::handleGuild)
        .build()
  }

  /**
   * Sends a public message to all clients in sight.
   */
  private fun handleGuild(chatMsg: ChatMessage) {
    if (chatMsg.chatMode != ChatMessage.Mode.GUILD) {
      LOG.warn { "Can not send non guild chat to guild" }
      return
    }

    awaitEntityResponse(messageApi, context, 5) {e ->
      val guildComp = e.tryGetComponent(GuildComponent::class.java)
          ?: return@awaitEntityResponse
      val copiedMessages = guildChatService.copyChatMessageToAllGuildMembers(guildComp.guildId, chatMsg)
      copiedMessages.forEach {
        sendToClientActor.tell(it, self)
      }
    }
  }

  companion object {
    const val NAME = "guild"
  }
}