package net.bestia.zone.economy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * I23: coin is not created by a settlement's books. Every coin a town gains for reasons that are not a
 * player - reverting toward what a town its size should hold, and trading with the world beyond the map -
 * is one the reserve no longer has.
 *
 * Before the reserve existed both of those conjured the money, which is what made the supply notional
 * however carefully it was configured.
 */
class CoinConservationTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val step = EconomyStep(catalogue, UndamagedCapacity(), UnclaimedProduction())

  private val village = SettlementReference.of(catalogue, settlement = 7, population = 300, wealth = 0.4, traffic = 1.5)

  @Test
  fun `what a settlement gains over a year is exactly what the reserve loses`() {
    var reserve = PLENTY
    var state = drained()
    val opening = reserve + state.treasury

    repeat(360) {
      val next = step.advance(village, state, state.lastStepDay + 1.0, reserve)
      reserve -= next.treasury - state.treasury
      state = next
    }

    assertTrue(state.treasury > 0.0, "the town never recovered, so nothing was drawn and nothing is proved")
    assertEquals(opening, reserve + state.treasury, 1e-6, "coin was created or destroyed over the year")
  }

  @Test
  fun `a glut sold outward returns coin rather than inventing somewhere to put it`() {
    var reserve = PLENTY
    var state = flush()
    val opening = reserve + state.treasury

    repeat(120) {
      val next = step.advance(village, state, state.lastStepDay + 1.0, reserve)
      reserve -= next.treasury - state.treasury
      state = next
    }

    assertTrue(state.treasury < flush().treasury, "the purse never came down, so nothing was handed back")
    assertTrue(reserve > PLENTY, "the reserve did not grow, so the coin the town gave up went nowhere")
    assertEquals(opening, reserve + state.treasury, 1e-6, "coin was created or destroyed on the way down")
  }

  @Test
  fun `an empty reserve leaves a drained town drained`() {
    val state = walk(drained(), reserve = 0.0, days = 90)

    assertEquals(
      0.0,
      state.treasury,
      1e-9,
      "a town refilled its strongbox out of a reserve that had nothing in it"
    )
  }

  @Test
  fun `and it still lets that town hand coin back`() {
    // The cap is on gaining, not on paying. Rationing a repayment would strand coin outside the supply.
    val state = walk(flush(), reserve = 0.0, days = 30)

    assertTrue(state.treasury < flush().treasury, "an empty reserve refused to take its own coin back")
  }

  @Test
  fun `a reserve nobody is short of changes nothing`() {
    // I14 rests on this: while the reserve does not bind, the step is the function it always was, so
    // splitting a catch-up in two still agrees with doing it in one.
    val rationed = walk(drained(), reserve = PLENTY, days = 45)
    val unlimited = walk(drained(), reserve = Double.POSITIVE_INFINITY, days = 45)

    assertEquals(unlimited.treasury, rationed.treasury, 1e-12, "a reserve with plenty in it altered the step")
  }

  private fun walk(from: LedgerState, reserve: Double, days: Int): LedgerState {
    var state = from
    var left = reserve

    repeat(days) {
      val next = step.advance(village, state, state.lastStepDay + 1.0, left)
      left -= next.treasury - state.treasury
      state = next
    }

    return state
  }

  /** Robbed: the stores emptied and the strongbox taken, which is what makes the reversion do work. */
  private fun drained(): LedgerState {
    return LedgerState(
      deltaStock = village.stock.mapValues { (_, reference) -> -reference },
      treasury = 0.0,
      lastStepDay = START,
    )
  }

  private fun flush(): LedgerState {
    return LedgerState(treasury = village.treasury * 3.0, lastStepDay = START)
  }

  private companion object {
    const val START = 412.0

    /** Far more than the village can absorb, so the cap never binds and the accounting is what is tested. */
    const val PLENTY = 1_000_000.0
  }
}
