package net.bestia.zone.breeding

import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.battle.status.IvStat
import net.bestia.zone.bestia.Sex
import net.bestia.zone.world.time.Season
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Rolls a clutch from its parents, per https://docs.bestia-game.net/docs/mechanics/bestias/bestia/#individual-value-evolution
 * and the "Learnable Attacks" section below it. Without spell scrolls the candidates are always drawn at random.
 */
class ClutchRoll(
  /** How many IVs take the better parent's value: three, four with Bloodline Study Lv. 4. */
  private val dominantIvs: Int,
  private val beastfriendLevel: Int,
  private val random: Random,
) {

  /** [offspringLearnset] is the mother's species' learnset: skill id to learn level. */
  fun roll(mother: ClutchParent, father: ClutchParent, offspringLearnset: Map<Long, Int>, season: Season): Clutch {
    return Clutch(
      speciesId = mother.speciesId,
      sex = Sex.roll(random),
      ivs = inheritIvs(mother.ivs, father.ivs),
      attackLearnLevels = inheritAttacks(father, offspringLearnset, season),
      hatchTime = BreedingTimes.hatching(mother.level, father.level, random),
    )
  }

  private fun inheritIvs(mother: IndividualValues, father: IndividualValues): IndividualValues {
    val dominant = IvStat.entries.shuffled(random).take(dominantIvs).toSet()

    return IndividualValues.of { stat ->
      val seed = if (stat in dominant) maxOf(mother[stat], father[stat]) else pickParent(mother[stat], father[stat])
      (seed * varianceFactor()).roundToInt().coerceIn(IndividualValues.MIN, IndividualValues.MAX)
    }
  }

  private fun pickParent(motherValue: Int, fatherValue: Int): Int {
    return if (random.nextBoolean()) motherValue else fatherValue
  }

  private fun varianceFactor(): Double {
    val roll = random.nextInt(100)
    return when {
      roll < 70 -> 1.1
      roll < 90 -> 1.0
      else -> 0.9
    }
  }

  private fun inheritAttacks(father: ClutchParent, offspringLearnset: Map<Long, Int>, season: Season): Map<Long, Int> {
    val band = learnLevelBandOf(season)
    val candidates = father.attacks
      .filter { (_, learnLevel) -> learnLevel in band && learnLevel <= father.level }
      .keys.shuffled(random).take(MAX_INHERITED_ATTACKS)

    val inherited = mutableMapOf<Long, Int>()
    for (skillId in candidates) {
      val fatherLearnLevel = father.attacks.getValue(skillId)
      if (random.nextInt(100) >= transferChancePercent(father.level, fatherLearnLevel)) continue

      val naturalLearnLevel = offspringLearnset[skillId]
      if (naturalLearnLevel != null && naturalLearnLevel <= fatherLearnLevel) continue

      val earlier = random.nextInt(100) < ONE_LEVEL_EARLIER_PERCENT
      inherited[skillId] = if (earlier) maxOf(1, fatherLearnLevel - 1) else fatherLearnLevel
    }

    return inherited
  }

  private fun transferChancePercent(fatherLevel: Int, learnLevel: Int): Int {
    return 30 + min(30, fatherLevel - learnLevel) + 2 * beastfriendLevel
  }

  companion object {
    const val MAX_INHERITED_ATTACKS = 3
    private const val ONE_LEVEL_EARLIER_PERCENT = 30

    /** Only attacks learned inside the season's band of levels can be passed on. */
    fun learnLevelBandOf(season: Season): IntRange {
      return when (season) {
        Season.SPRING -> 1..25
        Season.SUMMER -> 26..50
        Season.FALL -> 51..75
        Season.WINTER -> 76..100
      }
    }
  }
}
