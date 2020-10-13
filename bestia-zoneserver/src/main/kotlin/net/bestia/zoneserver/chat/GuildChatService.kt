package net.bestia.zoneserver.chat

import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatRequest
import net.bestia.messages.chat.ChatResponse
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.guild.GuildRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Sends chat message to all members of the guild.
 */
@Service
class GuildChatService(
    private val guildRepository: GuildRepository,
    private val accountRepository: AccountRepository
) {

  fun copyChatMessageToAllGuildMembers(guildId: Long, chatMessage: ChatRequest): List<ChatResponse> {
    val receivingGuild = guildRepository.findByIdOrNull(guildId)
        ?: return emptyList()

    // Get possible online members which have an active entity id set
    val activeMemberAccountIds = receivingGuild.allMembers()
        .filter { it.member.masterBestia?.entityId != 0L }
        .map {
          it.member.id
        }

    val sender = accountRepository.findOneOrThrow(chatMessage.accountId)
    val senderEntityId = sender.activeBestia?.entityId
    val senderNick = sender.activeBestia?.name

    val now = Instant.now().epochSecond
    return activeMemberAccountIds.map { accId ->
      ChatResponse(
          accountId = accId,
          chatMode = ChatMode.GUILD,
          senderNickname = senderNick,
          text = chatMessage.text,
          time = now,
          entityId = senderEntityId
      )
    }
  }
}