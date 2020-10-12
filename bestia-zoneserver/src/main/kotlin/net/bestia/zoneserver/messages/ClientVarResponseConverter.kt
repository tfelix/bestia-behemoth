package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.messages.ui.ClientVarResponse
import org.springframework.stereotype.Component

@Component
class ClientVarResponseConverter : MessageConverterOut<ClientVarResponse> {
  override fun convertToPayload(msg: ClientVarResponse): ByteArray {
    val builder = AccountProtos.ClientVarResponse.newBuilder()
    val protoMsg = builder.apply {
      key = msg.key
      value = msg.value
    }.build()

    return wrap { it.clientVarResponse = protoMsg }
  }

  override val fromMessage = ClientVarResponse::class.java
}