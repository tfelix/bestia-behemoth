package net.bestia.zone.ai.core.planner

/**
 * Search iterations one think sweep may still spend, shared by every agent it plans for.
 *
 * Only checked before a search starts: a started search runs to its own end, so a hard goal is never cut off
 * by a busy tick and retried from scratch forever.
 */
class PlanningBudget(remaining: Int) {

  var remaining: Int = remaining
    private set

  val isSpent: Boolean get() = remaining <= 0

  fun spend() {
    remaining--
  }
}
