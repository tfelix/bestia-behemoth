package net.bestia.zone.battle.skill

import net.bestia.zone.battle.Element

data class BattleAttack(
  val strength: Int,
  val manaCost: Int,
  val range: Long,
  val attackType: AttackType,
  val needsLineOfSight: Boolean,

  /**
   * Tiles in every direction from the aimed-at point, for an `AOE_GROUND` skill; null for every other
   * target type. Carried so a script damages the area the client drew rather than one it picked for
   * itself.
   */
  val aoeRadius: Double?,

  val attackElement: Element,
  val level: Int,
  val script: String?,
) {
  companion object {

    fun getBasicMeleeAttack(element: Element): BattleAttack {
      return BattleAttack(
        strength = 5,
        manaCost = 0,
        range = 1,
        attackType = AttackType.MELEE_PHYSICAL,
        needsLineOfSight = false,
        aoeRadius = null,
        attackElement = element,
        script = null,
        level = 1
      )
    }
  }
}
