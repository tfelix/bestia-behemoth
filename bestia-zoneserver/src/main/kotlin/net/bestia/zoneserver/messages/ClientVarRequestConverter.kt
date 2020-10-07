package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos
import net.bestia.messages.ui.ClientVarRequest
import org.springframework.stereotype.Component

@Component
class ClientVarRequestConverter : MessageConverter<ClientVarRequest>() {
  override val fromMessage: Class<ClientVarRequest> = ClientVarRequest::class.java
  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_REQUEST

  override fun convertToPayload(msg: ClientVarRequest): ByteArray {
    error("${fromMessage.simpleName} is not supposed to get send to the client")
  }

  override fun convertToMessage(msg: MessageProtos.Wrapper): ClientVarRequest {
    val proto = msg.clientVarRequest

    // FIXME insert account id via call
    val accountId = 1L

    return ClientVarRequest(
        accountId = accountId,
        key = proto.key,
        valueToSet = proto.valueToSet
    )
  }
}