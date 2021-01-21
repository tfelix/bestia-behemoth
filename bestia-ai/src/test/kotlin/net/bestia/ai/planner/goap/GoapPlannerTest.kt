package net.bestia.ai.planner.goap

import org.junit.jupiter.api.Test

internal class GoapPlannerTest {

  @Test
  fun test() {
    // TODO Create a class to intitially seed the planner with initial states, as well as provide him with available
    //   actions.
    val sut = GoapPlanner(
        initialState = setOf(
            AtPosition(0, 0, 0),
            HasItem(2, 2)
        ),
        goalState = setOf(HasHealth(100)),
        availableActions = mutableListOf(
            Action.useItem(1, 1, listOf(AddHealth(50))),
            Action.useItem(2, 1, listOf(AddMana(50))),
            Action.getItem(1),
            Action.useSpell(10, 20, listOf(AddHealth(100)))
        )
    )

    sut.plan()
  }
}