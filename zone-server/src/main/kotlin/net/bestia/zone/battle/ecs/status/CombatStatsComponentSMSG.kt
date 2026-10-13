package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.CombatStatsSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

data class CombatStatsComponentSMSG(
  override val entityId: Long,
  val atk: Int,
  val weaponAtk: Int,
  val matk: Int,
  val weaponMatk: Int,
  val hit: Int,
  val flee: Int,
  val crit: Int,
  val softDefense: Int,
  val hardDefense: Int,
  val aspd: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val combatStats = CombatStatsSMSGProto.CombatStatsSMSG.newBuilder()
      .setEntityId(entityId)
      .setAtk(atk)
      .setWeaponAtk(weaponAtk)
      .setMatk(matk)
      .setWeaponMatk(weaponMatk)
      .setHit(hit)
      .setFlee(flee)
      .setCrit(crit)
      .setSoftDefense(softDefense)
      .setHardDefense(hardDefense)
      .setAspd(aspd)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompCombatStats(combatStats)
      .build()
  }
}
