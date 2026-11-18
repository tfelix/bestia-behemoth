package net.bestia.zone.workexp

import net.bestia.zone.battle.ecs.level.LevelUpExperienceCalculator
import net.bestia.zone.config.WorkExpConfig
import org.springframework.stereotype.Component
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What an action of a given length pays at a given level.
 *
 * A level takes [LevelUpExperienceCalculator.levelSeconds] of constant work, so an action of `effort` seconds is
 * worth `pace * effort / levelSeconds` of the level, and EXP is that share of the level's cost. The share of a level
 * one action pays therefore falls as levels rise, which is what keeps high levels slow.
 */
@Component
class WorkExpCalculator(
  private val config: WorkExpConfig,
  private val levels: LevelUpExperienceCalculator,
) {

  /** The longest action [level] is paid for: what an item five levels above it takes to craft. */
  fun effortCap(level: Int): Double {
    val reach = level.coerceAtLeast(1) + REACH_LEVELS
    return GEAR_SECONDS_PER_LEVEL_SQUARED * reach * reach
  }

  fun payout(level: Int, effortSeconds: Double): Int {
    if (effortSeconds <= 0.0) return 0

    val effort = min(effortSeconds, effortCap(level))
    val share = min(config.pace * effort / levels.levelSeconds(level), config.payoutCapShare)
    return (share * levels.getRequiredExperience(level)).roundToInt()
  }

  private companion object {
    const val REACH_LEVELS = 5

    /** Craft time of a gear recipe is this times its item level squared, see `GearRecipeGenerator`. */
    const val GEAR_SECONDS_PER_LEVEL_SQUARED = 1.2
  }
}
