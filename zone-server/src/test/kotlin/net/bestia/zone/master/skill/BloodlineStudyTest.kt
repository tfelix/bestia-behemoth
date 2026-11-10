package net.bestia.zone.master.skill

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BloodlineStudyTest {

  @Test
  fun `each level takes five percent off the timers`() {
    assertEquals(0, BloodlineStudy.timeReductionPercent(0))
    assertEquals(5, BloodlineStudy.timeReductionPercent(1))
    assertEquals(25, BloodlineStudy.timeReductionPercent(5))
  }

  @Test
  fun `level four draws a fourth dominant IV`() {
    assertEquals(3, BloodlineStudy.dominantIvs(0))
    assertEquals(3, BloodlineStudy.dominantIvs(3))
    assertEquals(4, BloodlineStudy.dominantIvs(4))
    assertEquals(4, BloodlineStudy.dominantIvs(5))
  }

  @Test
  fun `a level past the table reads its last row`() {
    assertEquals(25, BloodlineStudy.timeReductionPercent(7))
  }

  @Test
  fun `exact numbers need level one`() {
    assertFalse(BloodlineStudy.readsExactIvs(0))
    assertTrue(BloodlineStudy.readsExactIvs(1))
  }
}
