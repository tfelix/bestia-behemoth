package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class SwapStoredBestiaHandler(
  private val storage: BestiaStorageService,
) : IoMessageHandler<SwapStoredBestiaCMSG> {

  override val wire = decoder(MessageCase.SWAP_STORED_BESTIA) { accountId, envelope ->
    SwapStoredBestiaCMSG.fromBnet(accountId, envelope.swapStoredBestia)
  }

  override fun handle(msg: SwapStoredBestiaCMSG): Boolean {
    storage.swap(msg.playerId, msg.storedPlayerBestiaId, msg.activeEntityId)

    return true
  }
}
