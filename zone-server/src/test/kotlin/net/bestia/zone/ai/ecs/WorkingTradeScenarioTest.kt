package net.bestia.zone.ai.ecs

import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.perception.SettlementWork
import net.bestia.zone.economy.Trade
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A miller with grain, and the same miller without any.
 *
 * The second half is the one worth having. `AiThinkSystem` plans for the top goal only, so a goal that is
 * available but unplannable leaves an agent with nothing at all - and a trade whose store has run dry is
 * exactly when that would first be seen, in a village a player has just burnt the fields around.
 */
class WorkingTradeScenarioTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(100, 100, 0)
  private val mill = Vec3L(140, 100, 0)

  private var stocked = true

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.workshops = object : SettlementWork {
      override fun tradeOf(business: String?): Trade? = if (business == "miller") MILLING else null
      override fun canSupply(settlement: Int, trade: Trade): Boolean = stocked
    }
  }

  @Test
  fun `a miller with grain turns out flour all shift`() {
    val miller = ai.spawnTownsfolk(MILLER, home = home, post = mill)

    ai.advanceTo(9)
    ai.tickUntil(
      maxTicks = 20 * 180,
      describe = { "never reached the mill (goal=${ai.goalNameOf(miller)}, at=${ai.positionOf(miller)})" }
    ) {
      ai.goalNameOf(miller) == "WorkShift" && ai.positionOf(miller).distance(mill) <= ARRIVED
    }

    ai.tickUntil(
      maxTicks = 20 * 120,
      describe = { "stood at the millstone without milling anything" }
    ) {
      madeToday() > 0
    }
  }

  @Test
  fun `and with the granary empty finds something else to do`() {
    stocked = false
    val miller = ai.spawnTownsfolk(MILLER, home = home, post = mill)

    ai.advanceTo(9)
    // They may well set off for the mill first: nothing has looked at the granary yet, and the sense that
    // does runs on its own slow interval. What must not happen is that they stay there.
    ai.tickUntil(
      maxTicks = 20 * 120,
      describe = { "a miller with nothing to mill never stopped trying (goal=${ai.goalNameOf(miller)})" }
    ) {
      ai.goalNameOf(miller) == "Loiter"
    }

    assertEquals(0, madeToday(), "milled a sack of grain the town does not have")
  }

  @Test
  fun `the grain running out mid-shift ends the shift and keeps the flour`() {
    val miller = ai.spawnTownsfolk(MILLER, home = home, post = mill)

    ai.advanceTo(9)
    ai.tickUntil(maxTicks = 20 * 300, describe = { "never milled anything to lose" }) { madeToday() > 0 }
    val milled = madeToday()

    stocked = false
    ai.tickUntil(
      maxTicks = 20 * 120,
      describe = { "went on working a mill with no grain in it (goal=${ai.goalNameOf(miller)})" }
    ) {
      ai.goalNameOf(miller) != "WorkShift"
    }

    assertTrue(milled > 0)
    assertEquals(milled, madeToday(), "the flour already milled was thrown out with the empty granary")
  }

  private fun madeToday(): Int {
    return ai.production.madeOn(SETTLEMENT, MILLING.produces, ai.now.absoluteDay.toLong())
  }

  private companion object {
    const val ARRIVED = TownsfolkDomain.DOORSTEP_RADIUS
    const val SETTLEMENT = 1

    val MILLER = Occupation("miller", "miller", "miller", HourWindow(7, 17), HourWindow(22, 6))

    val MILLING = Trade(
      id = "mill",
      business = "miller",
      sector = null,
      building = BuildingFunction.CRAFT,
      produces = "flour",
      consumes = listOf(Trade.Input("grain", 1.3)),
    )
  }
}
