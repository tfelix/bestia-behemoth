package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/** Runs for the doorway [net.bestia.zone.ai.perception.ShelterSense] picked. [GoHomeActionTemplate]'s twin. */
class GoToShelterActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "goToShelter"

  override fun ground(state: WorldState): List<Action> {
    val door = state.get(TownsfolkDomain.SHELTER_DOOR) ?: return emptyList()
    val position = state.get(TownsfolkDomain.POSITION) ?: return emptyList()
    if (position.distance(door) <= TownsfolkDomain.DOORSTEP_RADIUS) return emptyList()

    return listOf(
      Action(
        name = "goToShelter",
        effects = listOf(Effects.set(TownsfolkDomain.POSITION, door)),
        cost = { s -> (s.get(TownsfolkDomain.POSITION)?.distance(door) ?: Long.MAX_VALUE).toFloat() },
        behavior = { MoveTo(door, locomotion, arrivalRadius = TownsfolkDomain.DOORSTEP_RADIUS) },
      )
    )
  }
}
