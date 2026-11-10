package net.bestia.zone.master.skill

import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The Forester's `M_BLOODLINE_STUDY`: what a master's level in it does to their Breeding Farms, Incubators and to how
 * they read individual values. Not a `PassiveSkillScript`, for the reason `CraftBonusService` gives: it changes no
 * status value. Tables per https://docs.bestia-game.net/docs/mechanics/master/survival-tree/#skill-bloodline-study.
 */
@Service
class BloodlineStudy(
  private val skills: SkillRepository,
  private val learnedSkills: LearnedSkillRepository,
) {

  private val skillId: Long? by lazy { skills.findByIdentifier(SkillId.M_BLOODLINE_STUDY)?.id }

  /** For a caller off the tick, whose master may be offline. */
  @Transactional(readOnly = true)
  fun levelOf(masterId: Long): Int {
    val id = skillId ?: return 0
    return learnedSkills.findByMasterIdAndSkillId(masterId, id)?.level ?: 0
  }

  companion object {
    private val TIME_REDUCTION_PERCENT = intArrayOf(5, 10, 15, 20, 25)
    private val DOMINANT_IVS = intArrayOf(3, 3, 3, 4, 4)
    private const val UNSKILLED_DOMINANT_IVS = 3

    /** By how much the breeding and hatch timers are shorter, before the 50% cap. */
    fun timeReductionPercent(level: Int): Int {
      return at(TIME_REDUCTION_PERCENT, level, unskilled = 0)
    }

    fun dominantIvs(level: Int): Int {
      return at(DOMINANT_IVS, level, unskilled = UNSKILLED_DOMINANT_IVS)
    }

    /** Below level 1 a master reads only grades. */
    fun readsExactIvs(level: Int): Boolean {
      return level >= 1
    }

    // Clamped rather than throwing: a raised maxLevel should give the best documented value.
    private fun at(table: IntArray, level: Int, unskilled: Int): Int {
      if (level <= 0) return unskilled
      return table[(level - 1).coerceAtMost(table.lastIndex)]
    }
  }
}
