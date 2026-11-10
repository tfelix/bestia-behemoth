package net.bestia.zone.breeding

import java.time.Duration
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/** The breeding and hatch timers, per https://docs.bestia-game.net/docs/mechanics/bestias/bestia/#timers. */
object BreedingTimes {

  /** All reductions together never make a timer shorter than half its base. */
  const val MAX_REDUCTION_PERCENT = 50

  fun breeding(motherLevel: Int, fatherLevel: Int): Duration {
    val seconds = 25.0 * (motherLevel.toDouble() * fatherLevel).pow(1.3)
    return Duration.ofMillis((seconds * 1000).roundToLong())
  }

  /** Rolled once, when the egg is laid, so the egg's hatch time does not change when it moves. */
  fun hatching(motherLevel: Int, fatherLevel: Int, random: Random): Duration {
    val seconds = random.nextDouble(0.9, 1.0) * 18.0 * motherLevel * fatherLevel
    return Duration.ofMillis((seconds * 1000).roundToLong())
  }

  fun reduced(base: Duration, reductionPercent: Int): Duration {
    val capped = reductionPercent.coerceIn(0, MAX_REDUCTION_PERCENT)
    return base.multipliedBy(100L - capped).dividedBy(100L)
  }
}
