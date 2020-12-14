package net.bestia.ai.sensor

import net.bestia.ai.blackboard.Blackboard

/**
 * Sensor scan the environment and report their findings into a [Blackboard] which can be used
 * to decide with the AI modules.
 */
interface Sensor {
  /**
   * Detects every other entity which should be used for the action considerations.
   */
  fun detect(blackboard: Blackboard)
}