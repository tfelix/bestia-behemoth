package net.bestia.zone.spellscroll.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.spellscroll.SpellScrollBook
import net.bestia.zone.spellscroll.SpellScrollsSMSG
import org.springframework.stereotype.Component

/** Tells a client what scrolls hold, whoever holds them, so one that was traded or found shows its attack too. */
@Component
class SpellScrollsHandler(
  private val book: SpellScrollBook,
  private val out: OutMessageProcessor,
) : IoMessageHandler<SpellScrollsCMSG> {

  override val wire = decoder(MessageCase.SPELL_SCROLLS_REQUEST) { accountId, envelope ->
    SpellScrollsCMSG.fromBnet(accountId, envelope.spellScrollsRequest)
  }

  override fun handle(msg: SpellScrollsCMSG): Boolean {
    val scrolls = msg.uniqueIds.take(MAX_SCROLLS).mapNotNull(book::scrollOf)
    out.sendToPlayer(msg.playerId, SpellScrollsSMSG(scrolls))

    return true
  }

  private companion object {
    /** More than any bag holds; a longer list is a client asking about scrolls it never saw. */
    const val MAX_SCROLLS = 200
  }
}
