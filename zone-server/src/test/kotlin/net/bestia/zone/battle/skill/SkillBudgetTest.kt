package net.bestia.zone.battle.skill

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SkillBudgetTest {

  @Test
  fun `spends down to zero and then refuses`() {
    val budget = budget(maxOps = 3)

    repeat(3) { budget.charge() }

    assertEquals(0, budget.remainingOps)
    assertThrows<SkillBudgetExceededException> { budget.charge() }
  }

  @Test
  fun `a charge larger than what is left is refused rather than partly taken`() {
    val budget = budget(maxOps = 10)
    budget.charge(6)

    assertThrows<SkillBudgetExceededException> { budget.charge(5) }
    assertEquals(
      4,
      budget.remainingOps,
      "a refused charge must leave the budget untouched, or the error message lies about what was spent"
    )
  }

  @Test
  fun `a query is charged for its answer, not just for asking`() {
    val budget = budget(maxOps = 100)

    budget.chargeQueryResults(30)

    assertEquals(70, budget.remainingOps)
  }

  @Test
  fun `an answer over the cap is refused outright rather than truncated`() {
    // A script that silently saw half the entities in a cube would be a bug nobody could see.
    val budget = budget(maxOps = 10_000, maxQueryResults = 50)

    val ex = assertThrows<SkillBudgetExceededException> { budget.chargeQueryResults(51) }

    assertTrue(ex.message!!.contains("51"), "the message should say how big the answer was: ${ex.message}")
  }

  @Test
  fun `an empty answer costs nothing beyond the query itself`() {
    val budget = budget(maxOps = 5)

    budget.chargeQueryResults(0)

    assertEquals(5, budget.remainingOps)
  }

  @Test
  fun `a script that runs past its deadline is stopped even with ops to spare`() {
    // The clock is injected rather than slept through: a test that waited 250ms to prove this would be the
    // slowest in the suite and still flaky on a loaded machine.
    var nanos = 0L
    val budget = SkillBudget(maxOps = 1_000, maxQueryResults = 1_000, maxMillis = 10, clock = { nanos })

    budget.charge()
    nanos = 11_000_000L

    val ex = assertThrows<SkillBudgetExceededException> { budget.charge() }

    assertTrue(ex.message!!.contains("10ms"), "the message should name the deadline: ${ex.message}")
  }

  private fun budget(maxOps: Int, maxQueryResults: Int = 1_000) =
    SkillBudget(maxOps = maxOps, maxQueryResults = maxQueryResults, maxMillis = 60_000)
}
