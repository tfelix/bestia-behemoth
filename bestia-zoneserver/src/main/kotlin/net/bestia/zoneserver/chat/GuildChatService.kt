package net.bestia.zoneserver.chat

import net.bestia.messages.chat.ChatRequest
import net.bestia.messages.chat.ChatResponse
import net.bestia.model.guild.GuildRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class GuildChatService(
    private val guildRepository: GuildRepository
) {

  fun copyChatMessageToAllGuildMembers(guildId: Long, chatMessage: ChatRequest): List<ChatResponse> {
    val receivingGuild = guildRepository.findByIdOrNull(guildId)
        ?: return emptyList()
    val playerBestiaIds = receivingGuild.getPlayerBestiaIds()

    // TODO Fixme
    //  return playerBestiaIds.map { chatMessage.copy(accountId = it) }
    return emptyList()
  }
}