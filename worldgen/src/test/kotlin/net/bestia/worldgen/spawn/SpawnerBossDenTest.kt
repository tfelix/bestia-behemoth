package net.bestia.worldgen.spawn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a boss den may roll and which levels it covers, asked of the level curve directly.
 *
 * Bosses are authored from level 42 to 120 and tied to biomes, so a den must exist for both ends: the soft
 * ones where the wilds begin, the hardest only in the deepest corruption.
 */
class SpawnerBossDenTest {

  private val params = SpawnerParams()
  private val sut = SpawnerStage(params = params)

  @Test
  fun `wild country rolls rarely, corruption more often, gentler country never`() {
    val wild = ordinaryLevels(danger = 1.0, corrupted = false)
    val corrupted = ordinaryLevels(danger = 1.0, corrupted = true)
    val mid = ordinaryLevels(danger = 0.3, corrupted = false)

    assertEquals(params.wildBossChance, sut.bossChanceOf(wild, corrupted = false))
    assertEquals(params.corruptedBossChance, sut.bossChanceOf(corrupted, corrupted = true))
    assertTrue(params.corruptedBossChance > params.wildBossChance, "corruption must breed more bosses")
    assertEquals(0.0, sut.bossChanceOf(mid, corrupted = false), "mid country ${mid.min}..${mid.max} rolled")
  }

  @Test
  fun `the home ring keeps bosses out even on corrupted ground`() {
    val ordinary = sut.levelsAt(danger = 1.0, severity = 1.0, corrupted = true, nearHome = true, boss = false)

    assertEquals(0.0, sut.bossChanceOf(ordinary, corrupted = true))
  }

  @Test
  fun `the softest wild boss den admits the upper-mid bosses`() {
    val softest = generateSequence(0.0) { it + 0.001 }
      .takeWhile { it <= 1.0 }
      .first { sut.bossChanceOf(ordinaryLevels(it, corrupted = false), corrupted = false) > 0.0 }
    val boss = sut.levelsAt(softest, severity = 0.0, corrupted = false, nearHome = false, boss = true)

    assertTrue(boss.min <= 42 && boss.max >= 54, "the first wild boss den is ${boss.min}..${boss.max}")
  }

  @Test
  fun `only the deepest corruption reaches the boss cap`() {
    val deepest = sut.levelsAt(danger = 1.0, severity = 1.0, corrupted = true, nearHome = false, boss = true)
    val wildest = sut.levelsAt(danger = 1.0, severity = 0.0, corrupted = false, nearHome = false, boss = true)

    assertEquals(params.bossMaxLevel, deepest.max)
    assertTrue(wildest.max < params.bossMaxLevel, "uncorrupted wilds reach ${wildest.max}")
  }

  private fun ordinaryLevels(danger: Double, corrupted: Boolean): SpawnerStage.Levels {
    return sut.levelsAt(
      danger = danger,
      severity = if (corrupted) 1.0 else 0.0,
      corrupted = corrupted,
      nearHome = false,
      boss = false
    )
  }
}
