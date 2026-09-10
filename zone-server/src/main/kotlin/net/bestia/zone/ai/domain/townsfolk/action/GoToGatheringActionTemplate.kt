package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Walks to where the evening is happening.
 *
 * Grounds from anywhere, unlike the other walks: arriving *is* the goal, so an action that refused to
 * ground once you were there would leave the goal satisfied and unplannable at the same time.
 */
class GoToGatheringActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "goToGathering"

  override fun ground(state: WorldState): List<Action> {
    val spot = state.get(TownsfolkDomain.SOCIAL_POSITION) ?: return emptyList()

    return listOf(
      Action(
        name = "goToGathering",
        effects = listOf(Effects.set(TownsfolkDomain.POSITION, spot)),
        cost = { s -> (s.get(TownsfolkDomain.POSITION)?.distance(spot) ?: Long.MAX_VALUE).toFloat() },
        behavior = { MoveTo(spot, locomotion, arrivalRadius = TownsfolkDomain.DOORSTEP_RADIUS) },
      )
    )
  }
}
