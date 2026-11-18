package net.bestia.zone.battle.ecs.level

import net.bestia.zone.config.ExpCurveConfig
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * EXP needed to go from [level] to the next one: the time a level takes ([levelSeconds], see [ExpCurveConfig]) at
 * the EXP rate of an on-level kill, `(4·lv + 5)` per [ExpCurveConfig.referenceKillSeconds]. The docs carry the table:
 * https://docs.bestia-game.net/docs/mechanics/bestias/bestia/#experience
 *
 * Kill EXP itself is authored per species and does not depend on this.
 */
@Component
class LevelUpExperienceCalculator(
  private val curve: ExpCurveConfig = ExpCurveConfig(),
) {

  /** Seconds of constant play to go from [level] to the next one. */
  fun levelSeconds(level: Int): Double {
    val ratio = level.coerceAtLeast(1).toDouble() / curve.anchorLevel
    return max(curve.floorSeconds.toDouble(), curve.anchorHours * SECONDS_PER_HOUR * ratio.pow(curve.exponent))
  }

  /** EXP per second of an on-level fighter, which is what an effort second of work is paid against. */
  fun fighterRate(level: Int): Double {
    return (KILL_PER_LEVEL * level.coerceAtLeast(1) + KILL_BASE).toDouble() / curve.referenceKillSeconds
  }

  fun getRequiredExperience(level: Int): Int {
    return (levelSeconds(level) * fighterRate(level)).roundToInt()
  }

  private companion object {
    const val SECONDS_PER_HOUR = 3600.0

    /** The base EXP of a kill, `4·lv + 5`. */
    const val KILL_PER_LEVEL = 4
    const val KILL_BASE = 5
  }
}
