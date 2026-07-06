package net.bestia.zone.ai.planner

import net.bestia.zone.ai.planner.actions.ApproachTargetAction
import net.bestia.zone.ai.planner.actions.FleeToSafetyAction
import net.bestia.zone.ai.planner.actions.MeleeAttackAction
import net.bestia.zone.ai.planner.actions.WanderAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoapPlannerTest {

  private val planner = GoapPlanner()
  private val actions = listOf(
    ApproachTargetAction(),
    MeleeAttackAction(),
    FleeToSafetyAction(),
    WanderAction()
  )

  private val killGoal = WorldState.of(StateKey.TARGET_DEAD to true)

  @Test
  fun `chains approach then attack when target is out of range`() {
    val start = WorldState.of(
      StateKey.HAS_TARGET to true,
      StateKey.TARGET_IN_MELEE_RANGE to false
    )

    val plan = planner.plan(start, killGoal, actions)

    assertNotNull(plan)
    assertEquals(listOf("approach_target", "melee_attack"), plan!!.actions.map { it.id })
  }

  @Test
  fun `plans a single attack when already in melee range`() {
    val start = WorldState.of(
      StateKey.HAS_TARGET to true,
      StateKey.TARGET_IN_MELEE_RANGE to true
    )

    val plan = planner.plan(start, killGoal, actions)

    assertNotNull(plan)
    assertEquals(listOf("melee_attack"), plan!!.actions.map { it.id })
  }

  @Test
  fun `returns no plan when the goal is unreachable`() {
    // No target and no action can produce HAS_TARGET, so TARGET_DEAD is unreachable.
    val start = WorldState.of(StateKey.HAS_TARGET to false)

    val plan = planner.plan(start, killGoal, actions)

    assertNull(plan)
  }

  @Test
  fun `returns an empty plan when the goal already holds`() {
    val start = WorldState.of(StateKey.TARGET_DEAD to true)

    val plan = planner.plan(start, killGoal, actions)

    assertNotNull(plan)
    assertTrue(plan!!.isEmpty)
  }
}
