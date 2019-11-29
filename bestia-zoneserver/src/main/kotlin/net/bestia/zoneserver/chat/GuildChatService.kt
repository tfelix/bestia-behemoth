package net.bestia.zoneserver.chat

import net.bestia.messages.chat.ChatMessage
import net.bestia.model.findOne
import net.bestia.model.guild.GuildRepository
import org.springframework.stereotype.Service

@Service
class GuildChatService(
    private val guildRepository: GuildRepository
) {

  fun copyChatMessageToAllGuildMembers(guildId: Long, chatMessage: ChatMessage): List<ChatMessage> {
    val receivingGuild = guildRepository.findOne(guildId)
        ?: return emptyList()
    val playerBestiaIds = receivingGuild.getPlayerBestiaIds()

    return playerBestiaIds.map { chatMessage.copy(accountId = it) }
  }
}