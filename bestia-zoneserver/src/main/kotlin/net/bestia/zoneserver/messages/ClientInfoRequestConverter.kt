package net.bestia.zoneserver.messages

import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.client.ClientInfoRequest
import org.springframework.stereotype.Component

@Component
class ClientInfoRequestConverter : MessageConverter<ClientInfoRequest>() {
  override val fromMessage: Class<ClientInfoRequest> = ClientInfoRequest::class.java
  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_REQUEST

  override fun convertToPayload(msg: ClientInfoRequest): ByteArray {
    error("${fromMessage.simpleName} is not supposed to get send to the client")
  }

  override fun convertToMessage(msg: MessageProtos.Wrapper): ClientInfoRequest {
    // TODO get the account id externally.
    val accountId = 1L
    return ClientInfoRequest(accountId)
  }
}