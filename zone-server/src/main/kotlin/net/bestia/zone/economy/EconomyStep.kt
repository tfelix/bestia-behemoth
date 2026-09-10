package net.bestia.zone.economy

import org.springframework.stereotype.Service
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min

/**
 * A settlement's books, moved forward in time. A pure function: nothing here reads a clock, a database or
 * the world, so the same state and the same reference always give the same answer.
 *
 * ### Whole days, with the remainder carried
 *
 * The step advances one game-day at a time and leaves a part-day for the next call. That shape is not an
 * approximation, it is what makes I14 - `advance(advance(x, a), b) == advance(x, a + b)` - hold *exactly*,
 * because both sides run `floor(a + b)` identical sub-steps. Integrating the elapsed interval directly
 * would make the two disagree at every clamp and every Leontief minimum, and I14 is what lets a town be
 * caught up lazily when a player walks in rather than ticked with the world.
 *
 * ### Why nothing drifts
 *
 * Production is expressed as a *fraction* of the reference rather than as an absolute rate, so an
 * undamaged, well-supplied settlement produces exactly what it consumes and a deviation of zero is an
 * exact fixed point. Seasonality moves the reference under it without moving the deviation at all, which
 * is why a town's grain rises and falls with the harvest while its database row goes on not existing.
 *
 * Three independent reasons it cannot run away, any one of which suffices: the price deviation relaxes
 * toward a target that is itself clamped, so its bound is an invariant set; more stock always means more
 * spoilage and more export and never more production of the same good; and no positive feedback loop can
 * exist because production depends only on a good's *inputs*, which [EconomyCatalogue] checks is acyclic.
 */
@Service
class EconomyStep(
  private val catalogue: EconomyCatalogue,
  private val capacity: SettlementCapacity,
  private val budget: ProductionBudget,
) {

  /**
   * The books brought up to [toDay], the world calendar's absolute day.
   *
   * Returns new state and writes nothing. Whether a row appears is the caller's decision, and it has to
   * be: I18 says a player standing in a town changes nothing, and a read path able to create a row would
   * break that however carefully this is written.
   */
  fun advance(reference: SettlementReference, state: LedgerState, toDay: Double): LedgerState {
    val elapsed = toDay - state.lastStepDay
    if (elapsed <= 0.0) return state

    val days = floor(min(elapsed, MAX_STEP_DAYS)).toInt()

    var moved = state
    for (day in 0 until days) {
      moved = oneDay(reference, moved, moved.lastStepDay + 1.0)
    }

    // A jump past the ceiling loses the excess rather than deferring it. Advancing only thirty would have
    // the next pass simulate thirty more, so a town left for a year would never finish catching up.
    return if (elapsed > MAX_STEP_DAYS) moved.copy(lastStepDay = toDay) else moved
  }

  private fun oneDay(reference: SettlementReference, state: LedgerState, day: Double): LedgerState {
    val dayOfYear = day.mod(Commodity.DAYS_PER_YEAR.toDouble())

    val stock = HashMap(state.deltaStock)
    val price = HashMap(state.deltaLogPrice)

    // In topological order, so a day's grain is milled and baked the same day rather than a shock taking
    // one extra day to travel each stage of the chain.
    for (commodity in catalogue.topological) {
      val rate = reference.throughput[commodity.id] ?: continue
      if (rate <= 0.0) continue

      val expected = (reference.stock[commodity.id] ?: 0.0) * commodity.seasonAt(dayOfYear)
      val standing = visible(expected, stock[commodity.id] ?: 0.0, reference.warehouseCap(commodity.id))

      val made = rate * runFraction(reference, commodity.id, stock, dayOfYear) -
        budget.claimed(reference.settlement, commodity.id)

      // Everything below is a *deviation*: at full production `made` equals `rate`, the flow is zero, and
      // what is left is the decay pulling an existing deviation back toward nothing.
      val decayed = (stock[commodity.id] ?: 0.0) * exp(-(commodity.spoilPerDay + reference.kappa))
      stock[commodity.id] = clampToStore(decayed + (made - rate), expected, reference.warehouseCap(commodity.id))

      price[commodity.id] = relaxPrice(price[commodity.id] ?: 0.0, commodity, standing, rate, dayOfYear)
    }

    return LedgerState(
      deltaStock = stock,
      deltaLogPrice = price,
      treasury = reference.treasury + (state.treasury - reference.treasury) * exp(-1.0 / TREASURY_TAU_DAYS),
      lastStepDay = day,
    )
  }

  /**
   * How much of its reference output a trade managed today, from 0 to 1.
   *
   * The Leontief minimum: a trade runs at the smallest ratio its inputs allow. That single line is where
   * *burn the field, no grain, no flour, no bread* falls out, with no special case for it anywhere.
   *
   * The ratio is against the input's *seasonal* reference rather than its flat one, or a mill would be
   * judged to be running at half capacity every winter merely because there is less grain about than in
   * September.
   */
  private fun runFraction(
    reference: SettlementReference,
    commodity: String,
    stock: Map<String, Double>,
    dayOfYear: Double,
  ): Double {
    val trade = catalogue.producerOf(commodity) ?: return 1.0
    val possible = capacity.capacityOf(reference.settlement, trade.id).coerceIn(0.0, 1.0)

    return trade.consumes.fold(possible) { limit, input ->
      val expected = (reference.stock[input.commodity] ?: 0.0) *
        catalogue.commodityOrThrow(input.commodity).seasonAt(dayOfYear)
      if (expected <= 0.0) return@fold limit

      val standing = visible(expected, stock[input.commodity] ?: 0.0, reference.warehouseCap(input.commodity))
      min(limit, (standing / expected).coerceIn(0.0, 1.0))
    }
  }

  /**
   * The price deviation, one day nearer where this much stock says it should be.
   *
   * Convex relaxation toward a target that is itself clamped, which is I1: whatever the stock model does,
   * the price stays inside half to four times the reference, and it gets there continuously rather than
   * in a jump a player could be surprised by.
   */
  private fun relaxPrice(
    current: Double,
    commodity: Commodity,
    standing: Double,
    rate: Double,
    dayOfYear: Double,
  ): Double {
    val target = PriceCurve.targetFor(commodity, standing, rate, commodity.seasonAt(dayOfYear))

    return current + (target - current) * (1.0 - exp(-1.0 / PRICE_TAU_DAYS))
  }

  /** I3 and I4: never below nothing, never above what the warehouse holds. */
  private fun visible(expected: Double, delta: Double, cap: Double): Double {
    return (expected + delta).coerceIn(0.0, cap)
  }

  /**
   * Keeps the deviation inside what the store can actually express.
   *
   * Without the lower clamp a town hit repeatedly builds an invisible debt below empty and takes far
   * longer than I17's sixty days to come back from it, while looking exactly as empty the whole time.
   * The upper one is I4: the excess spoils on the pile rather than being remembered.
   */
  private fun clampToStore(delta: Double, expected: Double, cap: Double): Double {
    return delta.coerceIn(-expected, cap - expected)
  }

  companion object {

    /** I15. A town left alone for a year is caught up as a month, not as a year. */
    const val MAX_STEP_DAYS = 30.0

    /**
     * The floor on how fast trade drains a shortage, per game-day - an isolated hamlet with no road at
     * all. Positive, and I5 leans on that: a commodity that neither spoils nor trades would be an
     * accumulator no shock ever recovers from.
     */
    const val MIN_TRADE_RATE = 0.1

    /** Game-days a price takes to make most of its move, so a shortage is felt over a day or two. */
    private const val PRICE_TAU_DAYS = 2.0

    /** Game-days the treasury takes to revert to what a town that size ought to hold. */
    private const val TREASURY_TAU_DAYS = 20.0
  }
}
