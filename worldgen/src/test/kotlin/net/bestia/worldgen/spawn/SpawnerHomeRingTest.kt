package net.bestia.worldgen.spawn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That nothing inside a starter town's ring exceeds the cap, whichever path decided its level.
 *
 * The boss roll used to return before the clamp, so roughly one corrupted den in forty inside the ring came
 * out at [SpawnerParams.maxLevel]. `Invariants.checkSpawnersNearHomeAreGentle` does catch it, but only on a
 * seed that puts corrupted ground within nine kilometres of a home - one of the four in
 * `StandardWorldTest`'s sweep does, and it took a world per case to say so. Asking the level curve directly
 * costs nothing and states the rule rather than sampling for it.
 */
class SpawnerHomeRingTest {

  private val params = SpawnerParams()
  private val sut = SpawnerStage(params = params)

  /** Ground bad enough that the ordinary curve alone would break the cap, so the clamp is what is measured. */
  private val severe = 1.0

  @Test
  fun `a boss inside the ring is capped like everything else`() {
    val levels = levelsAt(nearHome = true, boss = true)

    assertEquals(
      params.homeMaxLevel,
      levels.max,
      "a boss den inside the home ring reaches level ${levels.max}, which is what the ring exists to prevent"
    )
    assertTrue(levels.min <= levels.max, "the range is inverted: ${levels.min}..${levels.max}")
  }

  @Test
  fun `a boss outside the ring is still a boss`() {
    // The other half of the claim: the cap is the ring, not a nerf to bosses.
    assertEquals(params.maxLevel, levelsAt(nearHome = false, boss = true).max)
  }

  @Test
  fun `corrupted ground inside the ring is capped`() {
    assertEquals(params.homeMaxLevel, levelsAt(nearHome = true, boss = false).max)
  }

  private fun levelsAt(nearHome: Boolean, boss: Boolean): SpawnerStage.Levels {
    return sut.levelsAt(
      danger = severe,
      severity = severe,
      corrupted = true,
      nearHome = nearHome,
      boss = boss
    )
  }
}
