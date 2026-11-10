package net.bestia.zone.breeding

import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.battle.status.IvStat
import net.bestia.zone.world.time.Season
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ClutchRollTest {

  private val random = Random(11)

  private fun parent(iv: Int, level: Int = 10, attacks: Map<Long, Int> = emptyMap(), species: Long = 1L): ClutchParent {
    return ClutchParent(species, level, IndividualValues.of { iv }, attacks)
  }

  private fun rolls(
    mother: ClutchParent,
    father: ClutchParent,
    dominantIvs: Int = 3,
    learnset: Map<Long, Int> = emptyMap(),
    season: Season = Season.SPRING,
    beastfriend: Int = 0,
  ): List<Clutch> {
    val roll = ClutchRoll(dominantIvs, beastfriend, random)
    return (1..300).map { roll.roll(mother, father, learnset, season) }
  }

  private fun Clutch.values(): List<Int> {
    return IvStat.entries.map { ivs[it] }
  }

  @Test
  fun `a dominant IV seeds from the better parent and drifts by at most ten percent`() {
    val clutches = rolls(parent(50), parent(80), dominantIvs = IvStat.entries.size)

    assertEquals(setOf(72, 80, 88), clutches.flatMap { it.values() }.toSet())
  }

  @Test
  fun `the other IVs seed from either parent`() {
    val clutches = rolls(parent(50), parent(80), dominantIvs = 0)

    assertEquals(setOf(45, 50, 55, 72, 80, 88), clutches.flatMap { it.values() }.toSet())
  }

  @Test
  fun `a raised IV never passes 100`() {
    val clutches = rolls(parent(100), parent(100))

    assertTrue(clutches.flatMap { it.values() }.all { it in 90..100 })
  }

  @Test
  fun `four dominant IVs carry the better parent on at least four stats`() {
    val clutches = rolls(parent(0), parent(100), dominantIvs = 4)

    assertTrue(clutches.all { clutch -> clutch.values().count { it >= 90 } >= 4 })
  }

  @Test
  fun `the offspring is of the mother's species, either sex`() {
    val clutches = rolls(parent(50, species = 7L), parent(50, species = 8L))

    assertTrue(clutches.all { it.speciesId == 7L })
    assertEquals(2, clutches.map { it.sex }.toSet().size)
  }

  @Test
  fun `only attacks in the season's band are passed on`() {
    val father = parent(50, level = 60, attacks = mapOf(SPRING_ATTACK to 10, SUMMER_ATTACK to 30, FALL_ATTACK to 55))

    val inherited = rolls(parent(50), father, season = Season.SUMMER, beastfriend = 5).flatMap { it.attackLearnLevels.keys }

    assertEquals(setOf(SUMMER_ATTACK), inherited.toSet())
  }

  @Test
  fun `an attack lands at the father's learn level or one earlier`() {
    val father = parent(50, level = 60, attacks = mapOf(SUMMER_ATTACK to 30))

    val levels = rolls(parent(50), father, season = Season.SUMMER).mapNotNull { it.attackLearnLevels[SUMMER_ATTACK] }

    assertEquals(setOf(29, 30), levels.toSet())
  }

  @Test
  fun `an attack the offspring learns as early by itself is not passed on`() {
    val father = parent(50, level = 60, attacks = mapOf(SUMMER_ATTACK to 30))

    val clutches = rolls(parent(50), father, learnset = mapOf(SUMMER_ATTACK to 30), season = Season.SUMMER)

    assertTrue(clutches.all { it.attackLearnLevels.isEmpty() })
  }

  @Test
  fun `an attack the offspring learns later is pulled down to the father's level`() {
    val father = parent(50, level = 60, attacks = mapOf(SUMMER_ATTACK to 30))

    val levels = rolls(parent(50), father, learnset = mapOf(SUMMER_ATTACK to 45), season = Season.SUMMER)
      .mapNotNull { it.attackLearnLevels[SUMMER_ATTACK] }

    assertTrue(levels.isNotEmpty())
    assertTrue(levels.all { it in 29..30 })
  }

  @Test
  fun `at most three attacks are tried per clutch`() {
    val attacks = (1L..6L).associateWith { 26 + it.toInt() }
    val father = parent(50, level = 100, attacks = attacks)

    assertTrue(rolls(parent(50), father, season = Season.SUMMER, beastfriend = 5).all { it.attackLearnLevels.size <= 3 })
  }

  @Test
  fun `the transfer chance grows with how far the father is past the attack`() {
    val justLearned = parent(50, level = 30, attacks = mapOf(SUMMER_ATTACK to 30))
    val wellPast = parent(50, level = 60, attacks = mapOf(SUMMER_ATTACK to 30))

    val fromJustLearned = rolls(parent(50), justLearned, season = Season.SUMMER).count { it.attackLearnLevels.isNotEmpty() }
    val fromWellPast = rolls(parent(50), wellPast, season = Season.SUMMER).count { it.attackLearnLevels.isNotEmpty() }

    // 30% against 60% of 300 rolls.
    assertTrue(fromJustLearned in 60..120, "$fromJustLearned")
    assertTrue(fromWellPast in 150..210, "$fromWellPast")
  }

  private companion object {
    const val SPRING_ATTACK = 100L
    const val SUMMER_ATTACK = 200L
    const val FALL_ATTACK = 300L
  }
}
