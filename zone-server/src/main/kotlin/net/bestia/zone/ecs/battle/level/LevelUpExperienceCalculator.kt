package net.bestia.zone.ecs.battle.level

import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * EXP needed to go from [level] to the next one, per the game docs:
 * https://docs.bestia-game.net/docs/mechanics/bestia/#experience
 *
 * Cubic, so every route slows down as the level rises. Kill EXP (`4·lv + 5` in the docs) is authored per
 * species and does not depend on this.
 */
@Component
class LevelUpExperienceCalculator {

  fun getRequiredExperience(level: Int): Int {
    val lv = level.toDouble()
    return (lv * lv * lv / 3 + 1.5 * lv + 15).roundToInt()
  }
}
