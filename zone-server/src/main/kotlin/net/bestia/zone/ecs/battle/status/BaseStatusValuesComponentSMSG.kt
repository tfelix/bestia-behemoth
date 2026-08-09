package net.bestia.zone.ecs.battle.status

import net.bestia.bnet.proto.BaseStatusValuesSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

data class BaseStatusValuesComponentSMSG(
  override val entityId: Long,
  val strength: Int,
  val intelligence: Int,
  val vitality: Int,
  val dexterity: Int,
  val willpower: Int,
  val agility: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val baseStatusValues = BaseStatusValuesSMSGProto.BaseStatusValuesSMSG.newBuilder()
      .setEntityId(entityId)
      .setStrength(strength)
      .setIntelligence(intelligence)
      .setVitality(vitality)
      .setDexterity(dexterity)
      .setWillpower(willpower)
      .setAgility(agility)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompBaseStatusValues(baseStatusValues)
      .build()
  }
}
