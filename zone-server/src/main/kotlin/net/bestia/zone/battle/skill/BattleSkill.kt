package net.bestia.zone.battle.skill

import net.bestia.zone.battle.Element
import net.bestia.zone.skill.Skill

data class BattleSkill(
  val strength: Int,
  val manaCost: Int,
  val range: Long,
  val skillType: SkillType,
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
        aoeRadius = null,
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
    aoeRadius = skill.aoeRadius,
    attackElement = attackElement,
    level = level,
    script = skill.script,
    castTime = skill.castTime
  )
}
