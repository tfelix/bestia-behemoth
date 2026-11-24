package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/** The client asks for the storage when the master opens the ledger or talks to a keeper. */
@Component
class OpenBestiaStorageHandler(
  private val storage: BestiaStorageService,
) : IoMessageHandler<OpenBestiaStorageCMSG> {

  override val wire = decoder(MessageCase.OPEN_BESTIA_STORAGE) { accountId, envelope ->
    OpenBestiaStorageCMSG.fromBnet(accountId, envelope.openBestiaStorage)
  }

  override fun handle(msg: OpenBestiaStorageCMSG): Boolean {
    storage.open(msg.playerId)

    return true
  }
}
