package net.bestia.zone.battle.skill

import net.bestia.zone.battle.Element
import net.bestia.zone.skill.Skill

data class BattleSkill(
  val strength: Int,
  val manaCost: Int,
  val range: Long,
  val skillType: SkillType,
  val needsLineOfSight: Boolean,
  val attackElement: Element,
  val level: Int,
  val script: String?,
  val castTime: Float = 0f
) {
  companion object {

    fun getBasicMeleeAttack(element: Element): BattleSkill {
      return BattleSkill(
        strength = 5,
        manaCost = 0,
        range = 1,
        skillType = SkillType.MELEE_PHYSICAL,
        needsLineOfSight = false,
        attackElement = element,
        script = null,
        level = 1
      )
    }
  }

  constructor(
    skill: Skill,
    attackElement: Element = Element.NORMAL,
    level: Int = 1
  ) : this(
    strength = skill.strength ?: 0,
    manaCost = skill.manaCost,
    range = skill.range?.toLong() ?: 1L,
    skillType = skill.type,
    needsLineOfSight = skill.needsLineOfSight,
    attackElement = attackElement,
    level = level,
    script = skill.script,
    castTime = skill.castTime
  )
}
