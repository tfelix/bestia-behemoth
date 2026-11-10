package net.bestia.zone.cartography.survey

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The needle game a survey is played as: a compass needle swings across a half circle and has to be stopped
 * inside a target arc, once for each of [NEEDLES] rounds. A harder survey swings faster and narrows the arc.
 *
 * The swing is a pure function of time, so the server judges a stop from how long into its round it came, the
 * same way the client drew it.
 */
data class SurveyMinigame(
  val swingPeriodMs: Int,
  val arcWidthDegrees: Int,
  val arcCentresDegrees: List<Int>,
) {

  /** Where the needle points [elapsedMs] into a round, -90 to +90 degrees. */
  fun needleAt(elapsedMs: Long): Double = MAX_SWING_DEGREES * sin(2 * PI * elapsedMs / swingPeriodMs)

  /** Whether every round was stopped inside its arc. */
  fun isHit(stopsMs: List<Long>): Boolean {
    if (stopsMs.size != arcCentresDegrees.size) return false

    return stopsMs.zip(arcCentresDegrees).all { (stop, centre) ->
      stop >= 0 && abs(needleAt(stop) - centre) <= arcWidthDegrees / 2.0
    }
  }

  companion object {
    const val NEEDLES = 3
    const val MAX_SWING_DEGREES = 90.0

    /**
     * The game for a survey of [difficulty]: from a slow swing and a 40 degree arc at 0, to a fast swing and a
     * 6 degree arc at 120 and beyond.
     */
    fun of(difficulty: Int, random: Random = Random.Default): SurveyMinigame {
      val d = difficulty.coerceIn(0, MAX_DIFFICULTY)

      return SurveyMinigame(
        swingPeriodMs = SLOWEST_PERIOD_MS - (SLOWEST_PERIOD_MS - FASTEST_PERIOD_MS) * d / MAX_DIFFICULTY,
        arcWidthDegrees = WIDEST_ARC - (WIDEST_ARC - NARROWEST_ARC) * d / MAX_DIFFICULTY,
        arcCentresDegrees = List(NEEDLES) { random.nextInt(-ARC_CENTRE_SPREAD, ARC_CENTRE_SPREAD + 1) },
      )
    }

    /** The docs' formula: tier base, +10 per km from charted land, +0.5 per percent mana, -10 per Cartography level. */
    fun difficulty(tier: SurveyTier, kmFromCharted: Int, manaFraction: Double, cartographyLevel: Int): Int {
      return (tier.baseDifficulty + 10 * kmFromCharted + (50 * manaFraction).toInt() - 10 * cartographyLevel)
        .coerceAtLeast(0)
    }

    private const val MAX_DIFFICULTY = 120
    private const val SLOWEST_PERIOD_MS = 2400
    private const val FASTEST_PERIOD_MS = 700
    private const val WIDEST_ARC = 40
    private const val NARROWEST_ARC = 6
    private const val ARC_CENTRE_SPREAD = 70
  }
}
