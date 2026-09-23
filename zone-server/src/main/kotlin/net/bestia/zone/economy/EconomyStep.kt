package net.bestia.zone.economy

import org.springframework.stereotype.Service
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
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
 * Two reasons it cannot run away, either of which suffices: the price deviation relaxes toward a target
 * that is itself clamped, so its bound is an invariant set; and more stock never means more production of
 * the same good, while always meaning more spoilage and more export.
 *
 * There is one positive feedback loop, added deliberately - a shortage makes imports dearer, which buys
 * fewer of them. It lives entirely inside that clamp, so it deepens an equilibrium rather than escaping
 * one. See [tradeAcross].
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
  fun advance(
    reference: SettlementReference,
    state: LedgerState,
    toDay: Double,
    reserve: Double = Double.POSITIVE_INFINITY,
  ): LedgerState {
    val elapsed = toDay - state.lastStepDay
    if (elapsed <= 0.0) return state

    val days = floor(min(elapsed, MAX_STEP_DAYS)).toInt()

    var moved = state
    var reserveLeft = reserve
    for (day in 0 until days) {
      val next = oneDay(reference, moved, moved.lastStepDay + 1.0, reserveLeft)
      reserveLeft -= next.treasury - moved.treasury
      moved = next
    }

    // A jump past the ceiling loses the excess rather than deferring it. Advancing only thirty would have
    // the next pass simulate thirty more, so a town left for a year would never finish catching up.
    return if (elapsed > MAX_STEP_DAYS) moved.copy(lastStepDay = toDay) else moved
  }

  private fun oneDay(
    reference: SettlementReference,
    state: LedgerState,
    day: Double,
    reserveLeft: Double,
  ): LedgerState {
    val dayOfYear = day.mod(Commodity.DAYS_PER_YEAR.toDouble())

    val stock = HashMap(state.deltaStock)
    val price = HashMap(state.deltaLogPrice)
    var purse = state.treasury

    // The town's tax base, gathered as the day is worked: what its trades actually made, against what
    // they would have made undamaged. Both at reference prices, so it weighs a loaf against a sack the
    // way the town's own income does.
    var earned = 0.0
    var earnable = 0.0

    // In topological order, so a day's grain is milled and baked the same day rather than a shock taking
    // one extra day to travel each stage of the chain. It is also the order the purse is spent in, which
    // means a town short of money buys the staple at the bottom of the chain before the luxuries above it.
    for (commodity in catalogue.topological) {
      val rate = reference.throughput[commodity.id] ?: continue
      if (rate <= 0.0) continue

      val expected = (reference.stock[commodity.id] ?: 0.0) * commodity.seasonAt(dayOfYear)
      val standing = visible(expected, stock[commodity.id] ?: 0.0, reference.warehouseCap(commodity.id))

      val run = runFraction(reference, commodity.id, stock, dayOfYear)
      val made = rate * run * restockingEffort(standing, expected, run) -
        budget.claimed(reference.settlement, commodity.id)

      earned += max(0.0, made) * commodity.refPrice
      earnable += rate * commodity.refPrice

      // Everything below is a *deviation*: at full production `made` equals `rate`, the flow is zero, and
      // what is left is the decay pulling an existing deviation back toward nothing.
      val spoiled = (stock[commodity.id] ?: 0.0) * exp(-commodity.spoilPerDay) + (made - rate)
      val traded = tradeAcross(reference, commodity, spoiled, purse, price[commodity.id] ?: 0.0)

      purse += traded.coins
      stock[commodity.id] = clampToStore(spoiled - traded.units, expected, reference.warehouseCap(commodity.id))

      price[commodity.id] = relaxPrice(price[commodity.id] ?: 0.0, commodity, standing, rate, dayOfYear)
    }

    return LedgerState(
      deltaStock = stock,
      deltaLogPrice = price,
      treasury = withinReserve(
        state.treasury,
        revertTreasury(purse, reference.treasury * livelihood(earned, earnable)),
        reserveLeft,
      ),
      lastStepDay = day,
    )
  }

  /**
   * What a town's purse tends back toward: taxes and trade the map does not simulate, in proportion to
   * what the town still makes.
   *
   * A town that has lost its workshops is poor as well as idle, and without this it would keep a full
   * income while producing nothing. What it does *not* do is set how hard damage bites overall - a fire
   * in the fields moves it very little, because a village's income is mostly the value of the bread at
   * the end of the chain and that keeps flowing while the stores last. [TREASURY_TAU_DAYS] is the figure
   * that governs that.
   */
  private fun livelihood(earned: Double, earnable: Double): Double {
    if (earnable <= 0.0) return 1.0

    return (earned / earnable).coerceIn(0.0, 1.0)
  }

  private fun revertTreasury(purse: Double, target: Double): Double {
    return target + (purse - target) * exp(-1.0 / TREASURY_TAU_DAYS)
  }

  /**
   * A settlement cannot end a day holding coin the world has not got left to give it.
   *
   * Only a gain is rationed. Coin moving the other way is being handed back, and refusing a repayment
   * would leave the world short of its own supply.
   */
  private fun withinReserve(before: Double, after: Double, reserveLeft: Double): Double {
    val gain = after - before
    if (gain <= reserveLeft) return after

    return before + max(0.0, reserveLeft)
  }

  /** What the outside world moved, and what the settlement's purse gained or lost by it. */
  private class Traded(val units: Double, val coins: Double)

  /**
   * Trade with the un-simulated world beyond the map, and what it costs.
   *
   * How fast a shortage can be covered from outside, and what covering it costs. κ is the roads.
   *
   * **Charging it to the treasury is what makes destruction bite.** Left free, a village with a road
   * imports its way out of a burnt field indefinitely and no amount of damage moves a price by more
   * than a couple of percent.
   *
   * At the *local* price, not the reference one, which is what makes the charge bite rather than merely
   * exist. A shortage is precisely when a town has to pay over the odds for a sack of grain, so the
   * cost of covering one rises with its depth and the imports throttle themselves. Priced flat, a
   * village's ordinary income buys very nearly the shortfall a fire creates - close enough to cancel it -
   * and the fire is invisible a fortnight later.
   *
   * The loop that implies is real and is bounded: dearer imports mean fewer, which means dearer still.
   * [PriceCurve] clamps the premium, so the whole thing lives inside a bounded set and settles at a
   * worse equilibrium rather than running away. A glut is symmetric - it sells outward cheap.
   *
   * That charge is also why [restockingEffort] has to exist. Imports were the only way a store could
   * refill, so making them cost money would otherwise strand a town that has been emptied and robbed -
   * exactly the state I17 promises is survivable.
   */
  private fun tradeAcross(
    reference: SettlementReference,
    commodity: Commodity,
    delta: Double,
    purse: Double,
    premium: Double,
  ): Traded {
    val wanted = delta * (1.0 - exp(-reference.kappa))
    val price = commodity.refPrice * reference.priceMultiplier * exp(PriceCurve.clamp(premium))

    // A glut is sold outward and pays; only the importing direction is rationed.
    if (wanted >= 0.0) return Traded(wanted, wanted * price)

    val affordable = if (price <= 0.0) 0.0 else max(0.0, purse) / price
    val units = -min(-wanted, affordable)

    return Traded(units, units * price)
  }

  /**
   * How much harder a settlement works when its stores are down, as a multiple of its ordinary output.
   *
   * Without this a town cannot restock itself at all: production is a *fraction* of a reference that
   * equals consumption, so full output exactly feeds the town and leaves the shelves wherever a shock
   * put them. Buying the shortfall in was covering for that, and only while imports were free - the
   * moment they are charged, an emptied town with an empty purse has no way back and I17 fails.
   *
   * A settlement has the headroom for it: it works only as much of its catchment as it eats, so the
   * effort is the fields it does not normally need and the rations it does not normally have to keep.
   *
   * Scaled by [run], because whatever stops the ordinary work stops the extra work too: there is no
   * working harder on ground that has burnt, and a mill with no grain cannot make up a shortfall by
   * turning faster. Without that scaling a town could very nearly shrug off a fire, which is the hole
   * this branch exists to close. Replanting elsewhere is real, but it is a season's work, and in this
   * model it arrives as the rain taking the scars.
   *
   * It is exactly one at the reference either way, so I16's fixed point survives untouched.
   */
  private fun restockingEffort(standing: Double, expected: Double, run: Double): Double {
    if (expected <= 0.0) return 1.0

    val shortfall = ((expected - standing) / expected).coerceAtLeast(0.0)

    return 1.0 + min(RESTOCK_CEILING, shortfall * RESTOCK_GAIN) * run
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

    /**
     * The most a settlement will exert itself to refill an empty store, over its ordinary output.
     *
     * Sets how long recovery takes, and is what I17's sixty days rest on: a store holding `n` days of
     * cover refills from empty in about `n / this`, so the deepest one in the catalogue - grain, at a
     * month - comes back inside the window with room to spare.
     */
    const val RESTOCK_CEILING = 0.6

    /** How far down a store has to be before that effort is fully committed - a third of it, here. */
    private const val RESTOCK_GAIN = 3.0

    /** Game-days a price takes to make most of its move, so a shortage is felt over a day or two. */
    private const val PRICE_TAU_DAYS = 2.0

    /**
     * Game-days the treasury takes to revert to what a town that size ought to hold.
     *
     * Two game-months, because this is the one figure that decides whether damage lasts. Reversion is
     * the only income the model gives a settlement, so it is also a subsidy of `treasury / this` coins a
     * day that a ruined town spends on imports - at twenty days that subsidy buys most of the shortfall
     * a fire creates, and the fire stops mattering. Rebuilding savings from nothing is slow work.
     */
    private const val TREASURY_TAU_DAYS = 60.0
  }
}
