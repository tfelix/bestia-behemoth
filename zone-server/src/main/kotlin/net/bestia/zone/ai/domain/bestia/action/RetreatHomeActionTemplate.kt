package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Walks an owned bestia home out of a fight, grounding [BestiaDomain.Goals.RETREAT]. Closer than
 * [ReturnHomeActionTemplate] goes, and only planned while courage says to get out, so an ordinary walk home
 * never ends on the doorstep.
 */
class RetreatHomeActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "retreatHome"

  override fun ground(state: WorldState): List<Action> {
    val home = state.get(BestiaDomain.HOME_POSITION) ?: return emptyList()
    // As in ReturnHomeActionTemplate: a walk cannot be planned without knowing where it starts.
    state.get(BestiaDomain.POSITION) ?: return emptyList()

    return listOf(
      Action(
        name = "retreatHome",
        preconditions = listOf(Precondition { s -> BestiaDomain.headsHome(s) }),
        effects = listOf(Effects.set(BestiaDomain.POSITION, home)),
        // Cheaper than returnHome, which plans to the same spot but arrives anywhere inside the wander radius:
        // at equal cost the search took that one, and a bestia already inside the radius never moved.
        cost = { s -> BestiaDomain.distanceOrMax(s.get(BestiaDomain.POSITION), home) * 0.5f },
        behavior = { MoveTo(home, locomotion, arrivalRadius = BestiaDomain.RETREAT_ARRIVAL_RADIUS) },
      )
    )
  }
}
