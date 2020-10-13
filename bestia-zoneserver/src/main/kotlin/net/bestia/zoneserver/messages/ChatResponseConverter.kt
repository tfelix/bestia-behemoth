package net.bestia.zoneserver.messages

import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatResponse
import net.bestia.messages.proto.ChatProtos
import org.springframework.stereotype.Component

@Component
class ChatResponseConverter : MessageConverterOut<ChatResponse> {

  override val fromMessage = ChatResponse::class.java

  override fun convertToPayload(msg: ChatResponse): ByteArray {
    val chatMode = when (msg.chatMode) {
      ChatMode.PUBLIC -> ChatProtos.ChatMode.PUBLIC
      ChatMode.PARTY -> ChatProtos.ChatMode.PARTY
      ChatMode.GUILD -> ChatProtos.ChatMode.GUILD
      ChatMode.WHISPER -> ChatProtos.ChatMode.WHISPER
      ChatMode.SYSTEM -> ChatProtos.ChatMode.SYSTEM
      ChatMode.GM_BROADCAST -> ChatProtos.ChatMode.GM_BROADCAST
      ChatMode.ERROR -> ChatProtos.ChatMode.ERROR
      ChatMode.COMMAND -> ChatProtos.ChatMode.COMMAND
      ChatMode.BATTLE -> ChatProtos.ChatMode.BATTLE
    }

    val msg = ChatProtos.ChatResponse.newBuilder()
        .setAccountId(msg.accountId)
        .setText(msg.text)
        .setEntityId(msg.entityId ?: 0L)
        .setSenderNickname(msg.senderNickname ?: "")
        .setMode(chatMode)
        .setTime(msg.time)

    return wrap { it.chatResponse = msg.build() }
  }
}