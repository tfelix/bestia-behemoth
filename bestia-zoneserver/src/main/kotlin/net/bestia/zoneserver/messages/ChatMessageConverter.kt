package net.bestia.zoneserver.messages

import net.bestia.messages.chat.ChatMode
import net.bestia.messages.chat.ChatRequest
import net.bestia.messages.proto.ChatProtos
import net.bestia.messages.proto.MessageProtos
import org.springframework.stereotype.Component

@Component
class ChatMessageConverter : MessageConverter<ChatRequest>() {
  override val fromMessage: Class<ChatRequest> = ChatRequest::class.java
  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.CHAT_REQUEST

  override fun convertToPayload(msg: ChatRequest): ByteArray {
    error("ChatRequest is not supposed to get send to the client")
  }

  override fun convertToMessage(msg: MessageProtos.Wrapper): ChatRequest {
    val proto = msg.chatRequest

    return ChatRequest(
        accountId = proto.accountId,
        text = proto.text,
        receiverNickname = proto.receiverNickname,
        chatMode = when (proto.mode) {
          ChatProtos.ChatMode.PUBLIC -> ChatMode.PUBLIC
          ChatProtos.ChatMode.BATTLE -> ChatMode.BATTLE
          ChatProtos.ChatMode.COMMAND -> ChatMode.COMMAND
          ChatProtos.ChatMode.ERROR -> ChatMode.ERROR
          ChatProtos.ChatMode.GM_BROADCAST -> ChatMode.GM_BROADCAST
          ChatProtos.ChatMode.GUILD -> ChatMode.GUILD
          ChatProtos.ChatMode.PARTY -> ChatMode.PARTY
          ChatProtos.ChatMode.SYSTEM -> ChatMode.SYSTEM
          ChatProtos.ChatMode.WHISPER -> ChatMode.WHISPER
          else -> error("Unknown proto mode: ${proto.mode}")
        }
    )
  }
}