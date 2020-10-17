package net.bestia.zoneserver.messages

import net.bestia.messages.proto.AccountProtos
import net.bestia.zoneserver.actor.client.ClientInfoResponse
import org.springframework.stereotype.Component

@Component
class ClientInfoResponseConverter : MessageConverterOut<ClientInfoResponse> {

  private val ownedBestiaBuilder = AccountProtos.OwnedBestiaInfo.newBuilder()
  private val builder = AccountProtos.ClientInfoResponse.newBuilder()

  override fun convertToPayload(msg: ClientInfoResponse): ByteArray {
    builder.clear()
    ownedBestiaBuilder.clear()

    val ownedBestias = msg.ownedBestias.map { ob ->
      ownedBestiaBuilder
          .setEntityId(ob.entityId)
          .setPlayerBestiaId(ob.playerBestiaId)
          .build()
    }

    return wrap {
      it.clientInfoResponse = builder.setMasterEntityId(msg.masterBestiaEntityId ?: 0)
          .setActiveEntityId(msg.activeEntityId)
          .setBestiaSlotCount(msg.bestiaSlotCount)
          .addAllOwnedBestias(ownedBestias)
          .build()
    }
  }

  override val fromMessage = ClientInfoResponse::class.java
}