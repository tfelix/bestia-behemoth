package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.client.ClientInfoRequest
import org.springframework.stereotype.Component

@Component
class ClientInfoRequestConverter : MessageConverterIn<ClientInfoRequest> {
  override fun convertToMessage(accountId: Long, msg: MessageProtos.Wrapper): ClientInfoRequest {
    return ClientInfoRequest(accountId)
  }

  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_REQUEST
}