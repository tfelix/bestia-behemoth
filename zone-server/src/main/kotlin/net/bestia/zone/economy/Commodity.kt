package net.bestia.zone.economy

import net.bestia.zone.environment.time.BestiaDateTime
import kotlin.math.cos

/**
 * One tradeable good, as the settlement ledger sees it.
 *
 * A real item from `items.yml` rather than an abstraction. That is not tidiness: the whole point of the
 * economy is that a player can walk out of a shop holding what a town produced, and a good with no item
 * behind it could never be handed over.
 *
 * @param refPrice the price before a settlement's own wealth and its stored deviation
 * @param spoilPerDay fraction of stock lost per game-day, and one half of what stops a commodity being an
 *   unbounded accumulator - see [EconomyCatalogue]'s I5 check for the other half
 * @param coverDays days of throughput a settlement holds. Sets the reference stock, and is also the level
 *   the price reads against: below its cover a town charges more
 * @param perCapitaPerDay what one resident eats a day, or zero for something only a trade consumes
 */
data class Commodity(
  val id: String,
  val item: String,
  val refPrice: Double,
  val spoilPerDay: Double,
  val coverDays: Double,
  val perCapitaPerDay: Double,
  val season: Season?,
) {

  init {
    require(refPrice > 0.0) { "Commodity '$id' needs a positive price, was $refPrice" }
    // At one it is gone by morning whatever a settlement does, which is a commodity nobody can trade.
    require(spoilPerDay in 0.0..<1.0) { "Commodity '$id' must spoil in [0, 1) per day, was $spoilPerDay" }
    require(coverDays > 0.0) { "Commodity '$id' needs positive cover-days, was $coverDays" }
    require(perCapitaPerDay >= 0.0) { "Commodity '$id' cannot have negative consumption" }
  }

  /** How much of a year's average is standing at [dayOfYear]. One for a good with no season. */
  fun seasonAt(dayOfYear: Double): Double {
    return season?.multiplierAt(dayOfYear) ?: 1.0
  }

  /**
   * A yearly swing in how much of this good there is.
   *
   * On the *reference* rather than on the ledger, which is what lets a town's grain rise and fall with the
   * harvest while its database row goes on not existing.
   */
  data class Season(val peakDay: Int, val amplitude: Double) {

    init {
      require(peakDay in 0 until DAYS_PER_YEAR) { "peak-day must be a day of the year, was $peakDay" }
      // At amplitude 1 the trough is zero stock, which divides by zero in every cover calculation.
      require(amplitude in 0.0..0.9) { "A season's amplitude must be in [0, 0.9], was $amplitude" }
    }

    fun multiplierAt(dayOfYear: Double): Double {
      val phase = 2.0 * Math.PI * (dayOfYear - peakDay) / DAYS_PER_YEAR
      return 1.0 + amplitude * cos(phase)
    }
  }

  companion object {
    const val DAYS_PER_YEAR = BestiaDateTime.DAYS_PER_MONTH * BestiaDateTime.MONTHS_PER_YEAR
  }
}
