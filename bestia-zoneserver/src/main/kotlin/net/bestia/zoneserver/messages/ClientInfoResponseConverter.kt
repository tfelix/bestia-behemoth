package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.client.ClientInfoResponse
import org.springframework.stereotype.Component

@Component
class ClientInfoResponseConverter : MessageConverter<ClientInfoResponse>() {
  override val fromMessage: Class<ClientInfoResponse> = ClientInfoResponse::class.java
  override val fromPayload: MessageProtos.Wrapper.PayloadCase = MessageProtos.Wrapper.PayloadCase.CLIENT_INFO_RESPONSE

  override fun convertToPayload(msg: ClientInfoResponse): ByteArray {
    val clientInfoResponse = AccountProtos.ClientInfoResponse.newBuilder()
        .apply {
          addAllOwnedBestiaEntityIds(msg.ownedBestiaEntityIds)
          msg.masterBestiaEntityId?.let { masterEntityId = it }
          bestiaSlotCount = msg.bestiaSlotCount
        }

    return wrap { it.clientInfoResponse = clientInfoResponse.build() }
  }
}