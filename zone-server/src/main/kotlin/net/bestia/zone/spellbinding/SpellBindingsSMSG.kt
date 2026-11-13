package net.bestia.zone.spellbinding

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SpellBindingsSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.message.SMSG

data class SpellBindingsSMSG(
  val bindings: List<SpellBindingBook.Entry>,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val msg = SpellBindingsSmsgProto.SpellBindingsSMSG.newBuilder()
      .addAllBindings(bindings.map(::toProto))

    return EnvelopeProto.Envelope.newBuilder().setSpellBindings(msg).build()
  }

  private fun toProto(entry: SpellBindingBook.Entry): SpellBindingsSmsgProto.SpellBinding {
    val bound = entry.bound
    val builder = SpellBindingsSmsgProto.SpellBinding.newBuilder()
      .setBindingId(bound.id)
      .setSkillId(bound.spell.skillId)
      .setLevel(bound.spell.level)
      .setTrigger(bound.trigger.id.toInt())
      .setFilter(bound.filter.id.toInt())
      .setItemUniqueId(entry.itemUniqueId ?: 0)
    entry.position?.let { builder.setPosition(Vec3OuterClass.Vec3.newBuilder().setX(it.x).setY(it.y).setZ(it.z)) }

    return builder.build()
  }
}
