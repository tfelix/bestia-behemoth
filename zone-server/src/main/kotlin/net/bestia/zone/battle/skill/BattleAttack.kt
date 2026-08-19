package net.bestia.zone.battle.skill

import net.bestia.zone.battle.Element
import net.bestia.zone.skill.Skill

/**
 * What is being used in a fight, whichever pathway resolved it: a basic attack built from the weapon,
 * or a catalogued skill projected by [of].
 *
 * The two pathways read different halves of it. [range], [needsLineOfSight], [aoeRadius] and [level] are
 * common ground - a script asks how far its own skill reaches for the same reason a swing does. [strength]
 * and [attackType] belong to the basic attack alone: a skill's damage comes out of its script, so nothing
 * on the skill pathway routes on either.
 */
data class BattleAttack(
  val strength: Int,
  val manaCost: Int,
  val range: Long,

  /**
   * Which basic-attack formula resolves this, per [AttackStrategyFactory]. Meaningless on the skill
   * pathway, which never consults it.
   */
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
  val castTime: Float = 0f,
) {

  companion object {

    /** The swing an entity with no weapon still has. Every mob attacks with this until equipment exists. */
    fun getBasicMeleeAttack(element: Element = Element.NORMAL): BattleAttack = BattleAttack(
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

    /**
     * Projects a catalogue row for a cast at [level]. [attackType] is filled in rather than derived: the
     * catalogue no longer carries one, because a skill's damage is its script's business.
     */
    fun of(
      skill: Skill,
      level: Int,
      attackElement: Element = Element.NORMAL
    ): BattleAttack = BattleAttack(
      strength = skill.strength ?: 0,
      manaCost = skill.manaCost,
      range = skill.range?.toLong() ?: 1L,
      attackType = AttackType.MELEE_PHYSICAL,
      needsLineOfSight = skill.needsLineOfSight,
      aoeRadius = skill.aoeRadius,
      attackElement = attackElement,
      level = level,
      script = skill.script,
      castTime = skill.castTime
    )
  }
}
