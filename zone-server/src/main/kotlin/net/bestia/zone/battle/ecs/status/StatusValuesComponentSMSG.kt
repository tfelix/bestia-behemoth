package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.StatusValuesSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
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

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val statusValues = StatusValuesSMSGProto.StatusValuesSMSG.newBuilder()
      .setStrength(strength)
      .setIntelligence(intelligence)
      .setVitality(vitality)
      .setDexterity(dexterity)
      .setWillpower(willpower)
      .setAgility(agility)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setStatusValues(statusValues))
  }
}
