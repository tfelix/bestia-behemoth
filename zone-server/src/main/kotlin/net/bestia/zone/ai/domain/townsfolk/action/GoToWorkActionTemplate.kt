package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/** Walks to the post. [GoHomeActionTemplate]'s twin, and grounds only from away for the same reason. */
class GoToWorkActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "goToWork"

  override fun ground(state: WorldState): List<Action> {
    val work = state.get(TownsfolkDomain.WORK_POSITION) ?: return emptyList()
    val position = state.get(TownsfolkDomain.POSITION) ?: return emptyList()
    if (position.distance(work) <= TownsfolkDomain.DOORSTEP_RADIUS) return emptyList()

    return listOf(
      Action(
        name = "goToWork",
        effects = listOf(Effects.set(TownsfolkDomain.POSITION, work)),
        cost = { s -> (s.get(TownsfolkDomain.POSITION)?.distance(work) ?: Long.MAX_VALUE).toFloat() },
        behavior = { MoveTo(work, locomotion, arrivalRadius = TownsfolkDomain.DOORSTEP_RADIUS) },
      )
    )
  }
}
