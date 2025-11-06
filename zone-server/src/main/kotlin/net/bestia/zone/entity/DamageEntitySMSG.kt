package net.bestia.zone.entity

import net.bestia.bnet.proto.DamageEntitySMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class DamageEntitySMSG(
  val entityId: EntityId,
  val sourceEntityId: EntityId,
  val attackId: Int,
  val damage: Int,
  val div: Int,
  val skillLevel: Int,
  val type: DamageType
) : SMSG {

  enum class DamageType {
    MISS,
    NORMAL,
    CRIT,
    DODGE,
    HEAL
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val damageEntitySMSG = DamageEntitySMSGProto.DamageEntitySMSG.newBuilder()
      .setEntityId(entityId)
      .setSourceEntityId(sourceEntityId)
      .setAttackId(attackId)
      .setDamage(damage)
      .setDiv(div)
      .setSkillLevel(skillLevel)
      .setType(mapDamageTypeToProto(type))
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setDamageEntity(damageEntitySMSG)
      .build()
  }

  private fun mapDamageTypeToProto(damageType: DamageType): DamageEntitySMSGProto.DamageType {
    return when (damageType) {
      DamageType.MISS -> DamageEntitySMSGProto.DamageType.MISS
      DamageType.NORMAL -> DamageEntitySMSGProto.DamageType.NORMAL
      DamageType.CRIT -> DamageEntitySMSGProto.DamageType.CRIT
      DamageType.DODGE -> DamageEntitySMSGProto.DamageType.DODGE
      DamageType.HEAL -> DamageEntitySMSGProto.DamageType.HEAL
    }
  }

  companion object {
    fun fromItemHeal(receiverEntityId: Long, amount: Int): DamageEntitySMSG {
      return DamageEntitySMSG(
        entityId = receiverEntityId,
        sourceEntityId = receiverEntityId,
        attackId = 0,
        damage = amount,
        div = 0,
        skillLevel = 0,
        type = DamageType.HEAL
      )
    }
  }
}
