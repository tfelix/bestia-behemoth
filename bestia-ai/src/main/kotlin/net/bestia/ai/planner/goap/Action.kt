package net.bestia.ai.planner.goap

interface Action {
  fun getCurrentStatus(): ActionStatus
  fun isValid(): Boolean
  fun cost(): Int
  fun heuristic(): Int
}