package net.bestia.zone.spawn

import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.bestia.persistence.Bestia
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * Carries a species' authored strength to the level one individual rolled.
 *
 * A species' health, mana, attributes and EXP are fitted for its [Bestia.referenceLevel] - the middle of its
 * range. Left alone, a creature rolled at the top of the range would be exactly as tough and as rewarding as one
 * at the bottom, with only the `level / 4` terms of its attack and defence telling them apart. They are
 * multiplied by `(level + offset) / (reference + offset)` instead, which is 1 at the reference level.
 *
 * Attack power and attack speed are not scaled: the first is a multiplier already, and the second is a trait of
 * the species rather than of its size.
 */
@Component
class MobLevelScaling(
  config: MobLevelConfig = MobLevelConfig()
) {

  private val offset = config.scalingOffset

  fun factor(level: Int, species: Bestia): Double {
    return (level + offset).toDouble() / (species.referenceLevel + offset)
  }

  fun health(level: Int, species: Bestia): Int {
    return scaled(species.health, factor(level, species), floor = minOf(1, species.health))
  }

  fun mana(level: Int, species: Bestia): Int {
    return scaled(species.mana, factor(level, species), floor = 0)
  }

  fun experience(level: Int, species: Bestia): Int {
    return scaled(species.experienceReward, factor(level, species), floor = 0)
  }

  fun attributes(level: Int, species: Bestia): StatusValues {
    val factor = factor(level, species)

    return StatusValues(
      strength = scaled(species.strength, factor, floor = 1),
      vitality = scaled(species.vitality, factor, floor = 1),
      intelligence = scaled(species.intelligence, factor, floor = 1),
      agility = scaled(species.agility, factor, floor = 1),
      willpower = scaled(species.willpower, factor, floor = 1),
      dexterity = scaled(species.dexterity, factor, floor = 1)
    )
  }

  private fun scaled(value: Int, factor: Double, floor: Int): Int {
    return (value * factor).roundToInt().coerceAtLeast(floor)
  }
}
