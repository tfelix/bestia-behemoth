package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.messages.ui.ClientVarResponse
import org.springframework.stereotype.Component

@Component
class ClientVarResponseConverter : MessageConverter<ClientVarResponse>() {
  override val fromMessage: Class<ClientVarResponse> = ClientVarResponse::class.java
  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_RESPONSE

  override fun convertToPayload(msg: ClientVarResponse): ByteArray {
    val builder = AccountProtos.ClientVarResponse.newBuilder()
    val protoMsg = builder.apply {
      key = msg.key
      value = msg.value
    }.build()

    return wrap { it.clientVarResponse = protoMsg }
  }

  override fun convertToMessage(msg: MessageProtos.Wrapper): ClientVarResponse {
    error("${fromMessage.simpleName} is not supposed to get send to the server")
  }
}