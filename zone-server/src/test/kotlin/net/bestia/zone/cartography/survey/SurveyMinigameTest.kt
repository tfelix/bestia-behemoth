package net.bestia.zone.cartography.survey

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurveyMinigameTest {

  private val game = SurveyMinigame(swingPeriodMs = 2000, arcWidthDegrees = 20, arcCentresDegrees = listOf(0, 90, -90))

  @Test
  fun `the needle swings from the centre out to both sides`() {
    assertEquals(0.0, game.needleAt(0), 1e-9)
    assertEquals(90.0, game.needleAt(500), 1e-9)
    assertEquals(-90.0, game.needleAt(1500), 1e-9)
  }

  @Test
  fun `stops inside every arc win the game`() {
    assertTrue(game.isHit(listOf(0, 500, 1500)))
  }

  @Test
  fun `one stop outside its arc loses the game`() {
    assertFalse(game.isHit(listOf(0, 500, 1000)))
  }

  @Test
  fun `a stop is needed for every needle`() {
    assertFalse(game.isHit(listOf(0, 500)))
    assertFalse(game.isHit(listOf(0, 500, 1500, 0)))
  }

  @Test
  fun `a stop before the round started is no stop`() {
    assertFalse(game.isHit(listOf(-2000, 500, 1500)))
  }

  @Test
  fun `a harder survey swings faster and narrows the arc`() {
    val easy = SurveyMinigame.of(0)
    val hard = SurveyMinigame.of(120)

    assertEquals(2400 to 40, easy.swingPeriodMs to easy.arcWidthDegrees)
    assertEquals(700 to 6, hard.swingPeriodMs to hard.arcWidthDegrees)
    assertEquals(hard.swingPeriodMs to hard.arcWidthDegrees, SurveyMinigame.of(500).let { it.swingPeriodMs to it.arcWidthDegrees })
  }

  @Test
  fun `distance from charted land and mana make it harder, the skill level easier`() {
    val base = SurveyMinigame.difficulty(SurveyTier.KM_3, kmFromCharted = 0, manaFraction = 0.0, cartographyLevel = 1)

    assertEquals(40, base)
    assertEquals(base + 20, SurveyMinigame.difficulty(SurveyTier.KM_3, 2, 0.0, 1))
    assertEquals(base + 25, SurveyMinigame.difficulty(SurveyTier.KM_3, 0, 0.5, 1))
    assertEquals(base - 20, SurveyMinigame.difficulty(SurveyTier.KM_3, 0, 0.0, 3))
  }

  @Test
  fun `the tier is chosen by its kilometres`() {
    assertEquals(SurveyTier.KM_5, SurveyTier.ofKilometres(5))
    assertEquals(null, SurveyTier.ofKilometres(2))
    assertEquals(null, SurveyTier.ofKilometres(null))
  }
}
