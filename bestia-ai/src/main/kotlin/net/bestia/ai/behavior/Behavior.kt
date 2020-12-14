package net.bestia.ai.behavior

import net.bestia.ai.Consideration

/**
 * Calculates the action to take for an agent depending on the state of the world.
 */
interface Behavior {
  /**
   * Calculates one or more multiple considerations which can be used to make a action plan.
   */
  fun consider(): Set<Consideration>
}