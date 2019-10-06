package net.bestia.zoneserver.chat

import net.bestia.messages.chat.ChatMessage
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOne
import net.bestia.model.guild.GuildRepository
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.guild.GuildService
import org.springframework.stereotype.Service

@Service
class GuildChatService(
    private val playerEntityService: PlayerEntityService,
    private val guildService: GuildService,
    private val playerBestiaRepository: PlayerBestiaRepository,
    private val guildRepository: GuildRepository
) {

  fun copyChatMessageToAllGuildMembers(guildId: Long, chatMessage: ChatMessage): List<ChatMessage> {
    val playerBestiaId = playerEntityService.getActivePlayerEntityId(chatMessage.accountId)
        ?: return emptyList()
    val receivingGuild = guildRepository.findOne(guildId)
        ?: return emptyList()
    val playerBestiaIds = receivingGuild.getPlayerBestiaIds()

    return playerBestiaIds.map { chatMessage.copy(accountId = it) }
  }
}