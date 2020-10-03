package net.bestia.messages.chat

import net.bestia.messages.AccountMessage

data class ChatRequest(
    override val accountId: Long,
    val chatMode: ChatMode,
    val text: String,
    val receiverNickname: String? = null,
    val chatMessageId: Int = 0
) : AccountMessage
