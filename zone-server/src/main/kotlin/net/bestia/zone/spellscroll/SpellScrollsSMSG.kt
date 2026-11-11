package net.bestia.zone.spellscroll

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SpellScrollsSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.spellscroll.persistence.InscribedScroll

data class SpellScrollsSMSG(
  val scrolls: List<InscribedScroll>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val msg = SpellScrollsSmsgProto.SpellScrollsSMSG.newBuilder()
      .addAllScrolls(scrolls.map(::toProto))

    return EnvelopeProto.Envelope.newBuilder().setSpellScrolls(msg).build()
  }

  private fun toProto(scroll: InscribedScroll): SpellScrollsSmsgProto.SpellScroll {
    return SpellScrollsSmsgProto.SpellScroll.newBuilder()
      .setUniqueId(scroll.uniqueId)
      .setSkillId(scroll.skillId)
      .setLevel(scroll.level)
      .build()
  }
}
