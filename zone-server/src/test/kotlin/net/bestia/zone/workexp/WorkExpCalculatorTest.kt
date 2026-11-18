package net.bestia.zone.workexp

import net.bestia.zone.battle.ecs.level.LevelUpExperienceCalculator
import net.bestia.zone.config.WorkExpConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class WorkExpCalculatorTest {

  private val levels = LevelUpExperienceCalculator()
  private val sut = WorkExpCalculator(WorkExpConfig(), levels)

  /** What crafting an item of the crafter's own level takes. */
  private fun ownLevelCraft(level: Int): Double = 1.2 * level * level

  @Test
  fun `an own level craft pays what the design table says`() {
    val expected = mapOf(10 to 162, 40 to 9504, 60 to 31752, 80 to 74880, 97 to 133119)

    expected.forEach { (level, exp) ->
      val paid = sut.payout(level, ownLevelCraft(level))
      assertTrue(abs(paid - exp) <= 1) { "level $level paid $paid, expected $exp" }
    }
  }

  /** Below the cap an effort second pays `pace` times what an on-level fighter earns in a second. */
  @Test
  fun `an effort second pays the pace share of a fighter's second`() {
    val paid = sut.payout(40, 100.0)

    assertEquals(0.75 * 100.0 * levels.fighterRate(40), paid.toDouble(), 1.0)
  }

  @Test
  fun `the share of a level an own level craft pays falls as levels rise`() {
    val shares = (20..120 step 5).map { sut.payout(it, ownLevelCraft(it)).toDouble() / levels.getRequiredExperience(it) }

    assertTrue(shares.zipWithNext().all { (lower, higher) -> higher < lower }) { "shares were $shares" }
  }

  @Test
  fun `reaching far above your level is paid as the level you can reach`() {
    val tooHigh = sut.payout(10, ownLevelCraft(40))

    assertEquals(sut.payout(10, sut.effortCap(10)), tooHigh)
    assertTrue(tooHigh < sut.payout(40, ownLevelCraft(40)))
  }

  @Test
  fun `one payout is never more than the cap share of a level`() {
    val paid = sut.payout(10, 1_000_000.0)

    assertEquals((levels.getRequiredExperience(10) * 0.35).roundToInt(), paid)
  }

  @Test
  fun `no effort pays nothing`() {
    assertEquals(0, sut.payout(50, 0.0))
    assertEquals(0, sut.payout(50, -5.0))
  }

  @Test
  fun `a trivial craft at a high level is not worth the time`() {
    val paid = sut.payout(90, 1.2)

    assertTrue(paid < 50) { "paid $paid" }
  }
}
