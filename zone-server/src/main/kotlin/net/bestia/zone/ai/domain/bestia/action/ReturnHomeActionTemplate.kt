package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/** Walks back to [BestiaDomain.HOME_POSITION], grounding [BestiaDomain.Goals.RETURN_HOME]. */
class ReturnHomeActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "returnHome"

  override fun ground(state: WorldState): List<Action> {
    val home = state.get(BestiaDomain.HOME_POSITION) ?: return emptyList()
    // A journey cannot be planned without knowing where it starts. Without this the search could use this
    // action's positional effect to *invent* a position it had never observed, and then chain further travel on
    // top of the invention — which is how a creature that did not yet know where it was would decide that the
    // first thing to do in life was go home.
    state.get(BestiaDomain.POSITION) ?: return emptyList()
    val radius = state.get(BestiaDomain.WANDER_RADIUS) ?: BestiaDomain.DEFAULT_WANDER_RADIUS

    return listOf(
      Action(
        name = "returnHome",
        effects = listOf(Effects.set(BestiaDomain.POSITION, home)),
        cost = { s -> BestiaDomain.distanceOrMax(s.get(BestiaDomain.POSITION), home).toFloat() },
        // Arriving anywhere inside the wander radius is home enough, and matches the goal's own test —
        // walking to the exact spawn tile would be a pointlessly precise errand.
        behavior = { MoveTo(home, locomotion, arrivalRadius = radius) },
      )
    )
  }
}
