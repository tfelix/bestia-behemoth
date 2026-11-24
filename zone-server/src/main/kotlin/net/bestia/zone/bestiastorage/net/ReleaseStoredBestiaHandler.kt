package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class ReleaseStoredBestiaHandler(
  private val storage: BestiaStorageService,
) : IoMessageHandler<ReleaseStoredBestiaCMSG> {

  override val wire = decoder(MessageCase.RELEASE_STORED_BESTIA) { accountId, envelope ->
    ReleaseStoredBestiaCMSG.fromBnet(accountId, envelope.releaseStoredBestia)
  }

  override fun handle(msg: ReleaseStoredBestiaCMSG): Boolean {
    storage.release(msg.playerId, msg.playerBestiaId)

    return true
  }
}
