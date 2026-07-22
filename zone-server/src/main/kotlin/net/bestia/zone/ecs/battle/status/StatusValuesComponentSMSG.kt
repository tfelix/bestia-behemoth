package net.bestia.zone.ecs.battle.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StatusValuesSMSGProto
import net.bestia.zone.message.EntitySMSG

data class StatusValuesComponentSMSG(
  override val entityId: Long,
  val strength: Int,
  val intelligence: Int,
  val vitality: Int,
  val dexterity: Int,
  val willpower: Int,
  val agility: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val statusValues = StatusValuesSMSGProto.StatusValuesSMSG.newBuilder()
      .setEntityId(entityId)
      .setStrength(strength)
      .setIntelligence(intelligence)
      .setVitality(vitality)
      .setDexterity(dexterity)
      .setWillpower(willpower)
      .setAgility(agility)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompStatusValues(statusValues)
      .build()
  }
}
