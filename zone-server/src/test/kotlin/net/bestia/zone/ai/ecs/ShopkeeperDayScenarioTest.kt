package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.perception.SettlementFood
import net.bestia.zone.ai.perception.SettlementWork
import net.bestia.zone.economy.Trade
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A shopkeeper's errand, which is the longest thing anybody in this domain does.
 *
 * Worth a scenario of its own because no single action gets from an empty counter to a full one, so the
 * planner has to *chain* four of them. Nothing in the code lists that sequence: it falls out of what each
 * action needs, and if it stops falling out this is what says so.
 */
class ShopkeeperDayScenarioTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(100, 100, 0)
  private val shop = Vec3L(140, 100, 0)
  private val bakery = Vec3L(100, 160, 0)

  /** The square, which is not the shop and not the counter - see TownSquares for why that matters. */
  private val square = Vec3L(60, 100, 0)

  private var baked = true

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.mealStall = SettlementFood.Stall(Vec3L(120, 60, 0), inStock = true)
    ai.square = square
    ai.workshops = object : SettlementWork {
      override fun tradeOf(business: String?): Trade? = null
      override fun canSupply(settlement: Int, trade: Trade): Boolean = true
      override fun supplierNear(at: Vec3L, business: String?): SettlementWork.Supply? {
        return if (business == "general_store") SettlementWork.Supply(bakery, baked) else null
      }
    }
  }

  @Test
  fun `the shopkeeper works out the whole errand for themselves`() {
    val keeper = ai.spawnTownsfolk(KEEPER, home = home, post = shop)

    ai.advanceTo(9)
    ai.tickUntilGoal(keeper, "Restock")

    assertEquals(
      listOf("goToSupplier", "collectStock", "goToWork", "deliverStock"),
      ai.agentOf(keeper).currentPlan?.actions?.map { it.name },
      "the shopkeeper did not plan the trip, the pickup, the walk back and the unloading as one errand"
    )
  }

  @Test
  fun `and then runs it, ending up back behind their own counter`() {
    val keeper = ai.spawnTownsfolk(KEEPER, home = home, post = shop)

    ai.advanceTo(9)
    ai.tickUntil(
      maxTicks = 20 * 240,
      describe = { "never collected anything (at=${ai.positionOf(keeper)}, goal=${ai.goalNameOf(keeper)})" }
    ) {
      ai.beliefOf(keeper, TownsfolkDomain.CARRYING_STOCK) == true
    }
    assertTrue(
      ai.positionOf(keeper).distance(bakery) <= ARRIVED,
      "loaded the basket ${ai.positionOf(keeper).distance(bakery)} tiles from the bakery"
    )

    ai.tickUntil(
      maxTicks = 20 * 240,
      describe = { "never got the goods onto the shelf (goal=${ai.goalNameOf(keeper)})" }
    ) {
      ai.beliefOf(keeper, TownsfolkDomain.RESTOCKED_ON_DAY) != null
    }

    assertTrue(ai.positionOf(keeper).distance(shop) <= ARRIVED, "unloaded somewhere other than the shop")
    assertEquals(false, ai.beliefOf(keeper, TownsfolkDomain.CARRYING_STOCK), "still carrying the basket")
  }

  @Test
  fun `the errand is a day's errand, not a loop`() {
    val keeper = ai.spawnTownsfolk(KEEPER, home = home, post = shop)

    ai.advanceTo(9)
    ai.tickUntil(maxTicks = 20 * 240, describe = { "never finished the errand" }) {
      ai.beliefOf(keeper, TownsfolkDomain.RESTOCKED_ON_DAY) != null
    }

    ai.tickUntil(
      maxTicks = 20 * 240,
      describe = { "went straight back out again instead of minding the shop" }
    ) {
      ai.goalNameOf(keeper) == "WorkShift"
    }
  }

  @Test
  fun `with the bakery empty there is no errand to run`() {
    // What burning the fields does to a shopkeeper. The failure guarded against is not the missing errand,
    // it is standing still: an available goal the planner cannot solve leaves the agent with no plan at
    // all, so a shop with nothing to fetch has to fall through to the day's work instead.
    baked = false
    val keeper = ai.spawnTownsfolk(KEEPER, home = home, post = shop)

    ai.advanceTo(9)
    ai.tickUntil(
      maxTicks = 20 * 120,
      describe = { "a shopkeeper with nothing to fetch stopped doing anything (${ai.goalNameOf(keeper)})" }
    ) {
      ai.goalNameOf(keeper) == "WorkShift"
    }

    assertEquals(null, ai.beliefOf(keeper, TownsfolkDomain.RESTOCKED_ON_DAY), "fetched bread nobody had baked")
  }

  @Test
  fun `the evening is spent out, and bedtime still wins`() {
    val keeper = ai.spawnTownsfolk(KEEPER, home = home, post = shop)

    ai.advanceTo(19)
    ai.tickUntil(
      maxTicks = 20 * 240,
      describe = { "never got to the square (goal=${ai.goalNameOf(keeper)}, at=${ai.positionOf(keeper)})" }
    ) {
      ai.goalNameOf(keeper) == "Socialise" && ai.positionOf(keeper).distance(square) <= ARRIVED
    }

    ai.advanceTo(TownsfolkDomain.BEDTIME_HOUR)
    ai.tickUntil(
      maxTicks = 20 * 120,
      describe = { "kept socialising past bedtime (goal=${ai.goalNameOf(keeper)})" }
    ) {
      ai.goalNameOf(keeper) != "Socialise"
    }
  }

  private companion object {
    const val ARRIVED = TownsfolkDomain.DOORSTEP_RADIUS

    val KEEPER = Occupation("shopkeeper", "shopkeeper", "general_store", HourWindow(8, 18), HourWindow(22, 6))
  }
}
