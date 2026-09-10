package net.bestia.zone.economy

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * I14: stepping twice is stepping once over the sum. What makes lazy catch-up and a global tick the same
 * thing, and therefore what lets a town nobody has visited for a week cost nothing until they arrive.
 *
 * ### The invariant had to be restated before it could be satisfied
 *
 * As `step(step(x, a), b) == step(x, a + b)` over continuous `a` and `b` it is not satisfiable at all: the
 * step contains a clamp, a Leontief minimum and a seasonal term that varies across the interval, so an
 * exact-equality test over random pairs fails on the first run and then gets weakened until it proves
 * nothing.
 *
 * So the step advances in **whole game-days and carries the fraction**, which is what these tests are
 * written against - and it turns out to hold exactly for fractional intervals too, because a call that
 * cannot complete a day advances nothing and leaves the remainder for the next one. Above thirty days it
 * is undefined by I15, and the last test here is what says so out loud.
 */
class EconomySemigroupTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val step = EconomyStep(catalogue, UndamagedCapacity(), UnclaimedProduction())

  private val village = SettlementReference.of(catalogue, settlement = 7, population = 300, wealth = 0.4, traffic = 1.5)

  @Test
  fun `two steps are one step over the sum, for whole days`() {
    for (a in 1..8) {
      for (b in 1..8) {
        assertSplitsEqually(a.toDouble(), b.toDouble())
      }
    }
  }

  @Test
  fun `and for fractions, because a part-day advances nothing and is carried`() {
    val random = Random(0xEC0_1L)

    repeat(400) {
      assertSplitsEqually(random.nextDouble(0.0, 6.0), random.nextDouble(0.0, 6.0))
    }
  }

  @Test
  fun `a shocked settlement is what makes the property mean anything`() {
    // At the reference the step is a no-op, so every split agrees trivially. The relaxation is where two
    // sequences of floating-point operations could disagree, and this is the state that runs it.
    val shocked = shocked()

    for (a in 1..6) {
      for (b in 1..6) {
        assertSplitsEqually(a.toDouble(), b.toDouble(), from = shocked)
      }
    }
  }

  @Test
  fun `I15 clamps one step to thirty days, and drops what it cannot simulate`() {
    val shocked = shocked()

    val jumped = step.advance(village, shocked, toDay = shocked.lastStepDay + 400.0)
    val walked = (1..30).fold(shocked) { state, _ -> step.advance(village, state, state.lastStepDay + 1.0) }

    assertEquals(walked.deltaStock, jumped.deltaStock, "a 400-day jump simulated something other than 30 days")
    assertEquals(
      shocked.lastStepDay + 400.0,
      jumped.lastStepDay,
      "the clock has to jump to now, or the next pass would simulate another thirty days and never catch up"
    )
  }

  @Test
  fun `going nowhere changes nothing`() {
    val shocked = shocked()

    assertEquals(shocked, step.advance(village, shocked, toDay = shocked.lastStepDay))
    assertEquals(shocked, step.advance(village, shocked, toDay = shocked.lastStepDay - 5.0), "time only runs forwards")
  }

  private fun assertSplitsEqually(a: Double, b: Double, from: LedgerState = LedgerState(lastStepDay = START)) {
    val once = step.advance(village, from, from.lastStepDay + a + b)
    val twice = step.advance(village, step.advance(village, from, from.lastStepDay + a), from.lastStepDay + a + b)

    assertEquals(once.lastStepDay, twice.lastStepDay, "the clocks disagree after $a + $b days")
    for (id in once.deltaStock.keys) {
      assertEquals(
        once.deltaStock.getValue(id),
        twice.deltaStock.getValue(id),
        "$id stock differs when $a + $b days is split"
      )
      assertEquals(
        once.deltaLogPrice.getValue(id),
        twice.deltaLogPrice.getValue(id),
        "$id price differs when $a + $b days is split"
      )
    }
    assertEquals(once.treasury, twice.treasury, "the treasury differs when $a + $b days is split")
  }

  /** Every store emptied and the treasury spent, which is the furthest from the reference a town can be. */
  private fun shocked(): LedgerState {
    val state = LedgerState(
      deltaStock = village.stock.mapValues { (_, reference) -> -reference },
      deltaLogPrice = village.stock.mapValues { 0.6 },
      treasury = 0.0,
      lastStepDay = START,
    )

    assertTrue(state.deltaStock.isNotEmpty(), "the catalogue has no commodities, so nothing here is tested")
    return state
  }

  private companion object {
    /** Not zero, so a bug that reads the absolute day where it should read the elapsed one shows up. */
    const val START = 412.0
  }
}
