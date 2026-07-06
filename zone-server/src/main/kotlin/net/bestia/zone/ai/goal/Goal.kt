package net.bestia.zone.ai.goal

import net.bestia.zone.ai.planner.WorldState

/**
 * A thing an NPC can want, identified by [name] (bound from the `goals[].name` key of an archetype)
 * and expressed as the [desiredState] the planner must reach. How strongly the NPC wants it is not
 * defined here — that comes from the archetype's utility considerations, scored by the UtilityScorer.
 */
interface Goal {
  val name: String
  val desiredState: WorldState
}
