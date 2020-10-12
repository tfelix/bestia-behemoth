package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.messages.proto.MessageProtos
import net.bestia.zoneserver.actor.client.ClientInfoResponse
import org.springframework.stereotype.Component

@Component
class ClientInfoResponseConverter : MessageConverterOut<ClientInfoResponse> {
  override fun convertToPayload(msg: ClientInfoResponse): ByteArray {
    val clientInfoResponse = AccountProtos.ClientInfoResponse.newBuilder()
        .apply {
          addAllOwnedBestiaEntityIds(msg.ownedBestiaEntityIds)
          msg.masterBestiaEntityId?.let { masterEntityId = it }
          bestiaSlotCount = msg.bestiaSlotCount
        }

    return wrap { it.clientInfoResponse = clientInfoResponse.build() }
  }

  override val fromMessage = ClientInfoResponse::class.java
}