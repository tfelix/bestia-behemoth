package net.bestia.zone.master.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.master.bestia.BestiaRenaming
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class RenameBestiaHandler(
  private val renaming: BestiaRenaming,
) : IoMessageHandler<RenameBestiaCMSG> {

  override val wire = decoder(MessageCase.RENAME_BESTIA) { accountId, envelope ->
    RenameBestiaCMSG.fromBnet(accountId, envelope.renameBestia)
  }

  override fun handle(msg: RenameBestiaCMSG): Boolean {
    renaming.rename(msg.playerId, msg.entityId, msg.name)

    return true
  }
}
