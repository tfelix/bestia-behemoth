package net.bestia.zone.ecs.battle.level

import org.springframework.stereotype.Component
import kotlin.math.pow

@Component
class LevelUpExperienceCalculator {

  fun getRequiredExperience(level: Int): Int {
    val c = 11

    return if (level <= 10) {
      // Phase 1: 30% per step
      (c * 1.35.pow(level)).toInt()
    } else {
      // Phase 2: base at 10 (end value of phase 1)
      val baseAt10 = c * 1.35.pow(10)
      val blocks = (level - 10) / 10 // full 10er steps after 10
      val rest = (level - 10) % 10 // the rest in the block

      (baseAt10 * 1.3.pow(blocks) * 1.15.pow(rest)).toInt()
    }
  }
}