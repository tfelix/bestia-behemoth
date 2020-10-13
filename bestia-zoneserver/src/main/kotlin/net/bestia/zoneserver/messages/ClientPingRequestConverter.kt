package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.client.PingRequest
import net.bestia.zoneserver.actor.client.PingResponse
import org.springframework.stereotype.Component

@Component
class ClientPingRequestConverter : MessageConverterIn<PingRequest>, MessageConverterOut<PingResponse> {
  override val fromPayload = MessageProtos.Wrapper.PayloadCase.PING_REQUEST
  override val fromMessage = PingResponse::class.java

  override fun convertToMessage(accountId: Long, msg: MessageProtos.Wrapper): PingRequest {
    val proto = msg.pingRequest

    return PingRequest(
        accountId = accountId,
        sequenceNumber = proto.sequenceNumber
    )
  }

  override fun convertToPayload(msg: PingResponse): ByteArray {
    val payload = AccountProtos.PingResponse.newBuilder()
        .setSequenceNumber(msg.sequenceNumber)
        .build()

    return wrap { it.pingResponse = payload }
  }
}