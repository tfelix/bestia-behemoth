package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/** Walks a guard back to the middle of its [BestiaDomain.GUARD_AREA], grounding [BestiaDomain.Goals.RETURN_TO_MIDDLE]. */
class ReturnToMiddleActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "returnToMiddle"

  override fun ground(state: WorldState): List<Action> {
    val middle = state.get(BestiaDomain.GUARD_AREA)?.center ?: return emptyList()
    // As in ReturnHomeActionTemplate: a walk cannot be planned without knowing where it starts.
    state.get(BestiaDomain.POSITION) ?: return emptyList()

    return listOf(
      Action(
        name = "returnToMiddle",
        effects = listOf(Effects.set(BestiaDomain.POSITION, middle), Effects.set(BestiaDomain.BACK_IN_MIDDLE, true)),
        cost = { s -> BestiaDomain.distanceOrMax(s.get(BestiaDomain.POSITION), middle).toFloat() },
        behavior = { MoveTo(middle, locomotion, arrivalRadius = BestiaDomain.ARRIVAL_RADIUS) },
      )
    )
  }
}
