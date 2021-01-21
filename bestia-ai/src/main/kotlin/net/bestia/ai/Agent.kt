package net.bestia.ai

import net.bestia.ai.behavior.Behavior
import net.bestia.ai.blackboard.Blackboard
import net.bestia.ai.planner.goap.Action
import net.bestia.ai.planner.goap.ActionStatus
import net.bestia.ai.planner.Planner
import net.bestia.ai.sensor.Sensor

class Agent(
    private val sensors: List<Sensor>,
    private val blackboard: Blackboard,
    private val planner: Planner,
    private val behavior: Behavior
) {

  private var currentAction: Action? = null

  /**
   * TODO: Add time and execution management.
   */
  fun tick() {
    /*
    sensors.forEach { it.detect(blackboard) }

    val currentActionStatus = currentAction?.getCurrentStatus()

    when (currentActionStatus) {
      null, ActionStatus.FINISHED, ActionStatus.FAILED -> plan()
      ActionStatus.RUNNING -> cu
    }
     */
  }

  private fun plan() {
    val considerations = behavior.consider()
    currentAction = planner.plan(considerations)
    // currentAction?.run()
  }
}