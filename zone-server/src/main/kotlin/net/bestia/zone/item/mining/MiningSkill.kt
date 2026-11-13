package net.bestia.zone.item.mining

import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillIds
import org.springframework.stereotype.Service

/** A master's level in Mining, and what it lets them break out of the rock. */
@Service
class MiningSkill(
  private val skillIds: SkillIds,
) {

  /** Resolved by identifier, because the id in `skills.yml` is content and this is code. */
  private val miningId: Long? get() = skillIds[SkillId.M_MINING]

  /** Resolves the skill id at boot, so the tick never reaches the database for it. */
  fun warmUp() {
    miningId
  }

  fun levelOf(known: KnownSkills?): Int {
    val id = miningId ?: return 0

    return known?.levelOf(id) ?: 0
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val UNTRAINED_RESOURCE_LEVEL = 10
    private const val RESOURCE_LEVELS_PER_LEVEL = 20
    private const val TIME_CUT_PER_LEVEL = 0.1

    /** The highest ore level a miner with [miningLevel] breaks out, or null when no ore is too hard for them. */
    fun maxResourceLevelAt(miningLevel: Int): Int? {
      if (miningLevel >= MAX_LEVEL) return null

      return maxOf(UNTRAINED_RESOURCE_LEVEL, RESOURCE_LEVELS_PER_LEVEL * miningLevel)
    }

    /** What is left of the time a swing takes, after [miningLevel]. */
    fun timeFactorAt(miningLevel: Int): Double {
      return 1.0 - TIME_CUT_PER_LEVEL * miningLevel.coerceIn(0, MAX_LEVEL)
    }
  }
}
