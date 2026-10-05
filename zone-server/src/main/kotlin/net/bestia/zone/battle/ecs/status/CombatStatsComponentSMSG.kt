package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.CombatStatsSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
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

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val combatStats = CombatStatsSMSGProto.CombatStatsSMSG.newBuilder()
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

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setCombatStats(combatStats))
  }
}
