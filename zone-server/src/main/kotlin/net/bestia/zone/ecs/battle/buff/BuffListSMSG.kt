package net.bestia.zone.ecs.battle.buff

import net.bestia.bnet.proto.BuffListSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

data class BuffListSMSG(
  override val entityId: Long,
  val buffs: List<BuffEntry>
) : EntitySMSG {

  data class BuffEntry(
    val buffId: Long,
    val level: Int,
    val remainingSeconds: Float,
    val debuff: Boolean
  )

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val buffList = BuffListSMSGProto.BuffListSMSG.newBuilder()
      .setEntityId(entityId)
      .addAllBuffs(
        buffs.map { entry ->
          BuffListSMSGProto.BuffEntry.newBuilder()
            .setBuffId(entry.buffId.toInt())
            .setLevel(entry.level)
            .setRemainingSeconds(entry.remainingSeconds)
            .setDebuff(entry.debuff)
            .build()
        }
      )

    return EnvelopeProto.Envelope.newBuilder()
      .setCompBuffs(buffList)
      .build()
  }
}
