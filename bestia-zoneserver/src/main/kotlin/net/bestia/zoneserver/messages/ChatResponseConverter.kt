package net.bestia.zoneserver.messages

import net.bestia.messages.chat.ChatResponse
import net.bestia.messages.proto.ChatProtos
import org.springframework.stereotype.Component

@Component
class ChatResponseConverter : MessageConverterOut<ChatResponse> {

  override val fromMessage = ChatResponse::class.java

  override fun convertToPayload(msg: ChatResponse): ByteArray {
    val msg = ChatProtos.ChatResponse.newBuilder()
        .setAccountId(msg.accountId)
        .setText(msg.text)

    return wrap { msg }
  }
}