package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatRequest
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.chat.GuildChatService
import net.bestia.zoneserver.entity.component.GuildComponent
import org.springframework.beans.factory.annotation.Qualifier

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
    private val messageApi: MessageApi,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : AbstractActor() {

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ChatRequest::class.java, this::handleGuild)
        .build()
  }

  /**
   * Sends a public message to all clients in sight.
   */
  private fun handleGuild(chat: ChatRequest) {
    if (chat.chatMode != ChatMode.GUILD) {
      LOG.warn { "Can not send non guild chat to guild" }
      return
    }

    awaitEntityResponse(messageApi, context, 5) { e ->
      val guildComp = e.tryGetComponent(GuildComponent::class.java)
          ?: return@awaitEntityResponse
      val copiedMessages = guildChatService.copyChatMessageToAllGuildMembers(guildComp.guildId, chat)
      copiedMessages.forEach {
        sendClientActor.tell(it, self)
      }
    }
  }

  companion object {
    const val NAME = "guild"
  }
}