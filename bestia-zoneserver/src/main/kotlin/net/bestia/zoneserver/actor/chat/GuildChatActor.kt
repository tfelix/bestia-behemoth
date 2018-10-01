package net.bestia.zoneserver.actor.chat

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.chat.ChatMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.guild.GuildService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Handles guild chats.
 * It sends the chat message to all online guild members.
 * If the user is no member of a guild it does nothing.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class GuildChatActor(
        private val guildService: GuildService,
        private val playerEntityService: PlayerEntityService
) : AbstractActor() {

  private val sendToClientActor = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(ChatMessage::class.java, this::handleGuild)
            .build()
  }

  /**
   * Sends a public message to all clients in sight.
   */
  private fun handleGuild(chatMsg: ChatMessage) {
    if (chatMsg.chatMode != ChatMessage.Mode.GUILD) {
      LOG.warn { "Message $chatMsg is no guild message." }
      return
    }

    val playerBestiaId = playerEntityService.getActivePlayerEntityId(chatMsg.accountId) ?: return

    guildService.getGuildMembersFromPlayer(playerBestiaId).stream()
            .filter { member -> member.entityId != 0L }
            .map { it.id }
            .map{ chatMsg.copy(accountId = it) }
            .forEach { msg -> sendToClientActor.tell(msg, self) }
  }

  companion object {
    const val NAME = "guild"
  }
}