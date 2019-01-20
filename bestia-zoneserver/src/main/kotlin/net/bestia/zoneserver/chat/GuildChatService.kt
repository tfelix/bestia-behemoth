package net.bestia.zoneserver.chat

import net.bestia.messages.chat.ChatMessage
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.guild.GuildService
import org.springframework.stereotype.Service

@Service
class GuildChatService(
    private val playerEntityService: PlayerEntityService,
    private val guildService: GuildService,
    private val playerBestiaRepository: PlayerBestiaRepository
) {

  fun copyChatMessageToAllGuildMembers(chatMessage: ChatMessage): List<ChatMessage> {
    val playerBestiaId = playerEntityService.getActivePlayerEntityId(chatMessage.accountId)
        ?: return emptyList()
    val playerGuild = guildService.getGuildOfPlayer(playerBestiaId)
        ?: return emptyList()
    val playerBestiaIds = playerGuild.getPlayerBestiaIds()

    return playerBestiaRepository.findAllById(playerBestiaIds)
        .filter { member -> member.entityId != 0L }
        .map { it.id }
        .map { chatMessage.copy(accountId = it) }
  }
}