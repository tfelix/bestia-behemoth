package net.bestia.ai.planner.goap

import net.bestia.ai.planner.goap.condition.HasHealth
import net.bestia.ai.planner.goap.condition.HasItem
import net.bestia.ai.planner.goap.condition.IsAtPosition
import net.bestia.ai.planner.goap.effect.AddHealth
import net.bestia.ai.planner.goap.effect.ChangeItemAmount
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Duration

internal class GoapPlannerTest {

  private val MAX_PLAN_DURATION = Duration.ofMinutes(60)

  @ExperimentalStdlibApi
  @Test
  fun `planner will use two potions to restore health`() {
    val sut = GoapPlanner(
        initialState = setOf(
            // AtPosition(0, 0, 0),
            // HasItem(2, 2),
            HasItem(1, 2)
        ),
        goalState = setOf(HasHealth(100)),
        availableActions = mutableListOf(
            Action.useItem(1, 1, listOf(AddHealth(50)))
            // Action.useItem(2, 1, listOf(AddMana(50))),
            // Action.getItem(1),
            // Action.useSpell(10, 20, listOf(AddHealth(100)))
        )
    )

    val actions = sut.plan(MAX_PLAN_DURATION)

    Assertions.assertEquals(2, actions.size)
    Assertions.assertEquals("UseItem", actions[0].name)
    Assertions.assertEquals("UseItem", actions[1].name)
  }

  @ExperimentalStdlibApi
  @Test
  fun `planner will find best solution to reach goal`() {
    val sut = GoapPlanner(
        initialState = setOf(
            IsAtPosition(0, 0, 0),
            HasItem(1, 1)
        ),
        goalState = setOf(HasHealth(100)),
        availableActions = mutableListOf(
            Action.useItem(1, 1, listOf(AddHealth(50))),
            Action.useItem(2, 1, listOf(AddHealth(50))),
            Action.moveTo(10, 10, 0, listOf(ChangeItemAmount(2, 1))),
            Action.useSpell(1, 20, listOf(AddHealth(100)))
        )
    )

    val actions = sut.plan(MAX_PLAN_DURATION)
  }
}