package net.bestia.zone.ecs.battle.effects

import net.bestia.bnet.proto.StatusEffectListSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

data class StatusEffectListSMSG(
  override val entityId: Long,
  val effects: List<StatusEffectEntry>
) : EntitySMSG {

  data class StatusEffectEntry(
    val effectId: Long,
    val level: Int,
    val remainingSeconds: Float,
    val debuff: Boolean
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val effectList = StatusEffectListSMSGProto.StatusEffectListSMSG.newBuilder()
      .setEntityId(entityId)
      .addAllEffects(
        effects.map { entry ->
          StatusEffectListSMSGProto.StatusEffectEntry.newBuilder()
            .setEffectId(entry.effectId.toInt())
            .setLevel(entry.level)
            .setRemainingSeconds(entry.remainingSeconds)
            .setDebuff(entry.debuff)
            .build()
        }
      )

    return EnvelopeProto.Envelope.newBuilder()
      .setCompEffects(effectList)
      .build()
  }
}
