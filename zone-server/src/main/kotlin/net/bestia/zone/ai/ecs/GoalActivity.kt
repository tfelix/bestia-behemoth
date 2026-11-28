package net.bestia.zone.ai.ecs

import net.bestia.zone.bestia.diary.BestiaState

/**
 * What an owned bestia shows on its status line while it pursues one of these goals, and the [state] its master's
 * report counts that time in. Self-care, fighting and hauling break off the work, so they name themselves.
 */
enum class GoalActivity(val state: BestiaState, private vararg val goals: String) {
  GATHERING(BestiaState.WORKING, "Gather"),
  HAULING(BestiaState.WORKING, "HaulToChest"),
  FETCHING_FOOD(BestiaState.RESTING, "RestockFood"),
  EATING(BestiaState.RESTING, "EatFromBag", "EatVegetation"),
  HEALING(BestiaState.RESTING, "HealFromBag"),
  FIGHTING(BestiaState.FIGHTING, "KillAttacker", "KillEnemy", "HelpAlly"),
  RETREATING(BestiaState.SHELTERING, "Retreat"),
  RESTING_AT_HOME(BestiaState.RESTING, "RestAtHome"),
  SLEEPING(BestiaState.RESTING, "Sleep");

  companion object {
    fun ofGoal(goalName: String?): GoalActivity? {
      return entries.firstOrNull { goalName in it.goals }
    }
  }
}
