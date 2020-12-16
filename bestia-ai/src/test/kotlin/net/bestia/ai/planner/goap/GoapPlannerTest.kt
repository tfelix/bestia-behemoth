package net.bestia.ai.planner.goap

import org.junit.jupiter.api.Test

internal class GoapPlannerTest {

  @Test
  fun test() {
    val sut = GoapPlanner(
        worldState = setOf(
            Precondition.atPosition(0, 0, 0),
            Precondition.hasItem(2, 2)
        ),
        goalState = mapOf("Health" to 100),
        availableActions = mutableListOf(
            Action.useItem(1, 1, mapOf("Health" to 50)),
            Action.useItem(2, 1, mapOf("Mana" to 50)),
            Action.getItem(1),
            Action.useSpell(10, 20, mapOf("Health" to 100))
        )
    )

    sut.plan()
  }
}