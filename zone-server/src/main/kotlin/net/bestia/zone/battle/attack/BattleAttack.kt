package net.bestia.zone.battle.attack

import net.bestia.zone.battle.Element

data class BattleAttack(
  val strength: Int,
  val manaCost: Int,
  val range: Long,
  val attackType: AttackType,
  val needsLineOfSight: Boolean,
  val attackElement: Element,
  val level: Int,
  val script: String?
) {
  companion object {

    fun getBasicMeleeAttack(element: Element): BattleAttack {
      return BattleAttack(
        strength = 5,
        manaCost = 0,
        range = 1,
        attackType = AttackType.MELEE_PHYSICAL,
        needsLineOfSight = false,
        attackElement = element,
        script = null,
        level = 1
      )
    }
  }

  constructor(
    attack: Attack,
    attackElement: Element = Element.NORMAL,
    level: Int = 1
  ) : this(
    strength = attack.strength ?: 0,
    manaCost = attack.manaCost,
    range = attack.range?.toLong() ?: 1L,
    attackType = attack.type,
    needsLineOfSight = attack.needsLineOfSight,
    attackElement = attackElement,
    level = level,
    script = attack.script
  )
}