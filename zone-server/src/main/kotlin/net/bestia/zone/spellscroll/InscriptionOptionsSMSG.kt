package net.bestia.zone.spellscroll

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.InscriptionOptionsSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class InscriptionOptionsSMSG(
  val options: List<Option>,
) : SMSG {

  data class Option(
    val bestiaEntityId: EntityId,
    val skillId: Long,
    val level: Int,
    val successPercent: Int,
    val mindBreakPercent: Int,
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val msg = InscriptionOptionsSmsgProto.InscriptionOptionsSMSG.newBuilder()
      .addAllOptions(options.map(::toProto))

    return EnvelopeProto.Envelope.newBuilder().setInscriptionOptions(msg).build()
  }

  private fun toProto(option: Option): InscriptionOptionsSmsgProto.InscriptionOption {
    return InscriptionOptionsSmsgProto.InscriptionOption.newBuilder()
      .setBestiaEntityId(option.bestiaEntityId)
      .setSkillId(option.skillId)
      .setLevel(option.level)
      .setSuccessPercent(option.successPercent)
      .setMindBreakPercent(option.mindBreakPercent)
      .build()
  }
}
