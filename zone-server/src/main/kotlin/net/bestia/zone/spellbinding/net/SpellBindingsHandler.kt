package net.bestia.zone.spellbinding.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.spellbinding.SpellBindingBook
import net.bestia.zone.spellbinding.SpellBindingsSMSG
import org.springframework.stereotype.Component

/** Lists the playing master's own bindings, for the cast menu that releases one. */
@Component
class SpellBindingsHandler(
  private val book: SpellBindingBook,
  private val sessions: ConnectionInfoService,
  private val out: OutMessageProcessor,
) : IoMessageHandler<SpellBindingsCMSG> {

  override val wire = decoder(MessageCase.SPELL_BINDINGS_REQUEST) { accountId, _ -> SpellBindingsCMSG(accountId) }

  override fun handle(msg: SpellBindingsCMSG): Boolean {
    val masterId = sessions.getMasterId(msg.playerId)
    out.sendToPlayer(msg.playerId, SpellBindingsSMSG(book.of(masterId)))

    return true
  }
}
