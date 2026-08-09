package net.bestia.zone.account.master.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EffortValueCostCalculatorTest {

  private val calculator = EffortValueCostCalculator()

  @Test
  fun `step cost follows the documented curve up to the creation cap`() {
    // Pinned rather than derived so a change to max(1, next / 3) is a conscious one - the client
    // mirrors this exact table in StatusAttribute.step_cost.
    val expected = listOf(1, 1, 1, 1, 1, 2, 2, 2, 3)

    val actual = (1..9).map { calculator.stepCost(it) }

    assertEquals(expected, actual)
  }

  @Test
  fun `step cost keeps escalating past the balanced value`() {
    // Nothing caps an attribute at 9 - not at creation, not in-game - so the curve has to keep working
    // above it.
    assertEquals(3, calculator.stepCost(10))
    assertEquals(3, calculator.stepCost(11))
    assertEquals(4, calculator.stepCost(12))
    assertEquals(10, calculator.stepCost(30))
  }

  @Test
  fun `cumulative cost matches the documented sums`() {
    val expected = listOf(1, 2, 3, 4, 5, 7, 9, 11, 14)

    val actual = (1..9).map { calculator.cumulativeCost(it) }

    assertEquals(expected, actual)
    assertEquals(0, calculator.cumulativeCost(0), "an attribute at 0 has cost nothing")
  }

  @Test
  fun `the creation budget is exactly an even spread at the balanced value`() {
    // This is how the budget was sized: an even 9 across all six attributes has to be affordable and
    // spend it to the last point.
    val evenSpread = 6 * calculator.cumulativeCost(EffortValueCostCalculator.BALANCED_EFFORT_VALUE)

    assertEquals(EffortValueCostCalculator.CREATION_EFFORT_POINTS, evenSpread)
  }

  @Test
  fun `the budget also buys lopsided builds, which is why there is no per-attribute cap`() {
    // The even spread is the *most* the budget buys per attribute across the board, so capping at 9
    // would leave it as the only legal distribution. Without a cap the same 84 points buy a specialist
    // instead: five attributes at the floor and one pushed as far as the budget reaches.
    val floor = EffortValueCostCalculator.MIN_EFFORT_VALUE_AT_CREATION
    val specialist = calculator.cumulativeCost(22) + 5 * calculator.cumulativeCost(floor)

    assertEquals(EffortValueCostCalculator.CREATION_EFFORT_POINTS, specialist)

    // And 22 really is the ceiling the budget imposes - one more point is unaffordable.
    val overspent = calculator.cumulativeCost(23) + 5 * calculator.cumulativeCost(floor)
    assertEquals(true, overspent > EffortValueCostCalculator.CREATION_EFFORT_POINTS)
  }

  @Test
  fun `range cost prices only the steps actually bought`() {
    // Buying up from 5 to 9 costs the 6/7/8/9 steps: 2 + 2 + 2 + 3.
    assertEquals(9, calculator.rangeCost(from = 5, to = 9))
    assertEquals(0, calculator.rangeCost(from = 9, to = 9))
    assertEquals(
      calculator.cumulativeCost(9),
      calculator.rangeCost(from = 0, to = 9),
      "counting up from 0 is the same as the cumulative cost"
    )
  }
}
