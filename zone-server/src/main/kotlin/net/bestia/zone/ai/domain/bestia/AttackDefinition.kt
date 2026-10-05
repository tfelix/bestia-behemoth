package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.battle.skill.BattleAttack
import net.bestia.zone.battle.status.AttackSpeed
import net.bestia.zone.bestia.DefaultAttack

/**
 * One attack a bestia can throw at a target, in whatever detail the planner needs to reason about it:
 * how close it has to be ([range]) and how expensive it nominally is ([baseCost]) before remembered
 * [EffectivenessKey] effectiveness adjusts that cost up or down. [id] is the name a log line and the
 * effectiveness memory use.
 */
sealed interface AttackDefinition {
  val id: String
  val range: Long
  val baseCost: Float
}

/**
 * An attack skill an AI profile lists. [skillId] is the `skills.yml` row `SkillExecutionService` resolves, so
 * the cast goes through the same pipeline a player's does - range, mana, line of sight and script included.
 */
data class SkillAttack(
  override val id: String,
  override val range: Long,
  override val baseCost: Float = 5f,
  val skillId: Long,
) : AttackDefinition

/**
 * What a bestia attacks with when it uses no skill: the species' [DefaultAttack] at the species' ASPD, never
 * authored in a profile. It costs a little more than a skill, so a skill that can be used is preferred.
 */
data class DefaultAttackDefinition(
  override val id: String,
  val attack: BattleAttack,
) : AttackDefinition {
  override val range: Long get() = attack.range
  override val baseCost: Float get() = COST

  companion object {
    private const val COST = 6f

    fun of(defaultAttack: DefaultAttack, aspd: Int): List<DefaultAttackDefinition> {
      val motionMs = AttackSpeed.baseMotionMs(aspd)
      val melee = DefaultAttackDefinition("melee", BattleAttack.getBasicMeleeAttack(baseAttackMotionMs = motionMs))
      val ranged = DefaultAttackDefinition("ranged", BattleAttack.getBasicRangedAttack(baseAttackMotionMs = motionMs))

      return listOfNotNull(melee.takeIf { defaultAttack.melee }, ranged.takeIf { defaultAttack.ranged })
    }
  }
}
