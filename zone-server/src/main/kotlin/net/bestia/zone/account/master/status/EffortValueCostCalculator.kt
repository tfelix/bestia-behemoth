package net.bestia.zone.account.master.status

import org.springframework.stereotype.Component

/**
 * Prices raising an effort value by one point. See the game docs' effort-value rules:
 * https://docs.bestia-game.net/docs/mechanics/statusvalues/#effort-values
 *
 * The docs' `effGainNeeded = max(1, nextEffValue / 3)` with integer division, which yields the
 * escalating curve below - the first five points of an attribute are cheap, then each band of three
 * costs one more:
 *
 * | next value        | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9  |
 * |-------------------|---|---|---|---|---|---|---|---|----|
 * | step cost         | 1 | 1 | 1 | 1 | 1 | 2 | 2 | 2 | 3  |
 * | cumulative from 0 | 1 | 2 | 3 | 4 | 5 | 7 | 9 | 11| 14 |
 *
 * Used by both ends of a master's life: [net.bestia.zone.account.master.MasterFactory] validating
 * the distribution picked on the creation screen, and [InvestStatusPointService] pricing a point
 * spent in-game. The two must agree, or a value costs a different amount depending on when it was
 * bought.
 */
@Component
class EffortValueCostCalculator {

  /**
   * Points needed to raise an effort value from `nextValue - 1` to [nextValue]. Priced on the value
   * being *bought*, not the one being left behind.
   */
  fun stepCost(nextValue: Int): Int = maxOf(1, nextValue / 3)

  /** Total points needed to reach [target] starting from 0. */
  fun cumulativeCost(target: Int): Int = (1..target).sumOf { stepCost(it) }

  /**
   * Total points needed to raise an effort value from [from] to [to], i.e. what a single batched
   * investment of `to - from` points into one attribute costs.
   */
  fun rangeCost(from: Int, to: Int): Int = (from + 1..to).sumOf { stepCost(it) }

  companion object {
    /**
     * Effort points a brand new master gets to distribute, spent to the last point. Sized as
     * `6 * cumulativeCost(`[BALANCED_EFFORT_VALUE]`)` so that an even spread at 9 across all six
     * attributes is affordable.
     */
    const val CREATION_EFFORT_POINTS = 84

    /** Every attribute starts here, so the six mandatory first points come out of the budget too. */
    const val MIN_EFFORT_VALUE_AT_CREATION = 1

    /**
     * There is deliberately **no** per-attribute cap at creation. The budget is already the only
     * ceiling that matters: with the other five attributes at [MIN_EFFORT_VALUE_AT_CREATION] a single
     * one can reach 22 and no further, so a lopsided build is possible but not unbounded.
     *
     * A 9 cap would make this a cap of exactly the budget - `6 * cumulativeCost(9)` **is**
     * [CREATION_EFFORT_POINTS] - leaving 9/9/9/9/9/9 as the only distribution that spends it, and the
     * whole allocation screen pointless. This constant is that even spread, used only as the neutral
     * default for fixtures.
     */
    const val BALANCED_EFFORT_VALUE = 9
  }
}
