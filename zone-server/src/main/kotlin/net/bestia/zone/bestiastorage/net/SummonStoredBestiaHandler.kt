package net.bestia.zone.bestiastorage.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.bestiastorage.BestiaStorageService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class SummonStoredBestiaHandler(
  private val storage: BestiaStorageService,
) : IoMessageHandler<SummonStoredBestiaCMSG> {

  override val wire = decoder(MessageCase.SUMMON_STORED_BESTIA) { accountId, envelope ->
    SummonStoredBestiaCMSG.fromBnet(accountId, envelope.summonStoredBestia)
  }

  override fun handle(msg: SummonStoredBestiaCMSG): Boolean {
    storage.summon(msg.playerId, msg.playerBestiaId)

    return true
  }
}
