package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ai.domain.townsfolk.Occupation
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.perception.SettlementFood
import net.bestia.zone.ecs.entity.Animation
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * One villager, one whole day, through every stage of the real pipeline.
 *
 * The first branch where a day has enough in it to be worth walking end to end: a shift to turn up for,
 * a meal to break off for, a fight to hide from and a bed to go to. Each step below names the failure
 * mode it would catch, because a scenario test that only asserts the happy path tells you nothing about
 * *which* thing broke when it goes red.
 *
 * The clock is jumped rather than waited out - a game-hour is twenty real minutes - and the drives are
 * forced for the same reason. What is not faked is any of the deciding: the planner, the behaviour
 * trees and the movement all run.
 */
class VillagerDayScenarioTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(100, 100, 0)
  private val post = Vec3L(140, 100, 0)
  private val stall = Vec3L(120, 100, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
    ai.mealStall = SettlementFood.Stall(stall, inStock = true)
  }

  @Test
  fun `a labourer's whole day, in order`() {
    val villager = ai.spawnTownsfolk(LABOURER, home = home, post = post)

    // Dawn. Nothing pressing, so the floor goal has to catch them - a townsperson with no goal at all
    // stands rooted, and that is what LOITER exists to prevent.
    ai.advanceTo(6)
    ai.tickUntilGoal(villager, "Loiter")

    // The shift. A post outside the loiter radius is the case where GoHome would otherwise turn them
    // round the moment they arrived.
    ai.advanceTo(9)
    ai.tickUntil(
      maxTicks = 20 * 180,
      describe = { "never reached the post (goal=${ai.goalNameOf(villager)}, at=${ai.positionOf(villager)})" }
    ) {
      ai.goalNameOf(villager) == "WorkShift" && ai.positionOf(villager).distance(post) <= ARRIVED
    }

    // Lunch. The three-step plan is the longest the domain has, and it is the one that proves a
    // priority *scaled* by hunger can still outrank a flat one - see TownsfolkDomain.Goals.EAT.
    ai.setDrive(villager, TownsfolkDomain.HUNGER, 95)
    ai.tickUntilGoal(villager, "Eat")
    assertEquals(
      listOf("goToMeal", "buyFood", "eat"),
      ai.agentOf(villager).currentPlan?.actions?.map { it.name },
      "the villager did not plan the walk, the purchase and the meal as one errand"
    )

    ai.tickUntil(
      maxTicks = 20 * 180,
      describe = { "never got to the counter (at=${ai.positionOf(villager)})" }
    ) {
      ai.positionOf(villager).distance(stall) <= ARRIVED
    }
    ai.tickUntil(
      maxTicks = 20 * 60,
      describe = { "stood at the counter without eating (hunger=${ai.beliefOf(villager, TownsfolkDomain.HUNGER)})" }
    ) {
      (ai.beliefOf(villager, TownsfolkDomain.HUNGER) ?: 100) <= TownsfolkDomain.FED_HUNGER
    }

    // Back to work, which is what says the errand ended rather than becoming the new day.
    ai.tickUntil(
      maxTicks = 20 * 180,
      describe = { "never went back to work (goal=${ai.goalNameOf(villager)})" }
    ) {
      ai.goalNameOf(villager) == "WorkShift" && ai.positionOf(villager).distance(post) <= ARRIVED
    }

    // The shift ends on the world clock, not on a countdown the behaviour tree keeps - a relative timer
    // would run past midnight, which is what UntilHour exists to avoid.
    ai.advanceTo(LABOURER.shift!!.toHour)
    ai.tickUntil(describe = { "still at work after the shift (goal=${ai.goalNameOf(villager)})" }) {
      ai.goalNameOf(villager) != "WorkShift"
    }

    // Bed. Sleeping is gated on standing at one's own door, so this is a journey the planner works out
    // rather than a place they happen to be.
    ai.advanceTo(TownsfolkDomain.BEDTIME_HOUR)
    ai.tickUntil(
      maxTicks = 20 * 240,
      describe = { "never got to bed (goal=${ai.goalNameOf(villager)}, at=${ai.positionOf(villager)})" }
    ) {
      ai.animationOf(villager) == Animation.AnimationKind.SLEEP
    }
    assertTrue(
      ai.positionOf(villager).distance(home) <= ARRIVED,
      "fell asleep ${ai.positionOf(villager).distance(home)} tiles from home rather than in their own bed"
    )
  }

  @Test
  fun `with the town's larder empty they go hungry rather than freezing`() {
    // The failure this guards is not going hungry, it is standing still. AiThinkSystem plans for the
    // top goal only, so an available goal with no plan leaves the agent with nothing at all - and a
    // village whose field has been burnt is exactly when that would first be seen.
    ai.mealStall = SettlementFood.Stall(stall, inStock = false)
    val villager = ai.spawnTownsfolk(LABOURER, home = home)

    ai.advanceTo(12)
    ai.setDrive(villager, TownsfolkDomain.HUNGER, 100)

    ai.tickUntil(describe = { "never decided on anything at all while hungry with nothing to buy" }) {
      ai.goalNameOf(villager) != null
    }
    assertEquals("Loiter", ai.goalNameOf(villager), "bought a meal from a town that has none")
  }

  @Test
  fun `and out in the country, where there is no counter at all`() {
    ai.mealStall = null
    val villager = ai.spawnTownsfolk(LABOURER, home = home)

    ai.advanceTo(12)
    ai.setDrive(villager, TownsfolkDomain.HUNGER, 100)

    ai.tickUntil(describe = { "a farmhand with nowhere to buy food stopped doing anything" }) {
      ai.goalNameOf(villager) == "Loiter"
    }
  }

  private companion object {
    /** The doorstep radius, which is what every arrival in this domain is measured against. */
    const val ARRIVED = TownsfolkDomain.DOORSTEP_RADIUS

    val LABOURER = Occupation("labourer", "labourer", null, HourWindow(7, 17), HourWindow(22, 6))
  }
}
