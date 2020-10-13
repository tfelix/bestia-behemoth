package net.bestia.zoneserver.chat

import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatRequest
import net.bestia.messages.chat.ChatResponse
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PublicChatService(
    private val accountRepository: AccountRepository
) {

  fun getChatResponse(chatRequest: ChatRequest): ChatResponse {
    require(chatRequest.chatMode == ChatMode.PUBLIC) {
      "Chat Mode must be PUBLIC, was ${chatRequest.chatMode}"
    }

    val sender = accountRepository.findOneOrThrow(chatRequest.accountId)
    val senderNick = sender.activeBestia?.name
    val senderEntityId = sender.activeBestia?.entityId

    return ChatResponse(
        accountId = chatRequest.accountId,
        chatMode = ChatMode.PUBLIC,
        senderNickname = senderNick,
        text = chatRequest.text,
        time = Instant.now().epochSecond,
        entityId = senderEntityId
    )
  }
}