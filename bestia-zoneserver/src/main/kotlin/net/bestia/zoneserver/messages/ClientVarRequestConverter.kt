package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos
import net.bestia.messages.ui.ClientVarRequest
import org.springframework.stereotype.Component

@Component
class ClientVarRequestConverter : MessageConverterIn<ClientVarRequest> {
  override fun convertToMessage(accountId: Long, msg: MessageProtos.Wrapper): ClientVarRequest {
    val proto = msg.clientVarRequest

    return ClientVarRequest(
        accountId = accountId,
        key = proto.key,
        valueToSet = proto.valueToSet
    )
  }

  override val fromPayload = MessageProtos.Wrapper.PayloadCase.CLIENT_VAR_REQUEST
}