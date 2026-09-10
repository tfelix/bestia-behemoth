package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.leaves.StandStill
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Stays pressed into the doorway until the fight is over.
 *
 * The precondition is what makes the planner chain `goToShelter -> shelterAtDoor` from anywhere else,
 * exactly as the doorstep gate does for going to bed.
 *
 * [StandStill] never finishes, and that is the mechanism rather than a stub: nothing a townsperson does
 * ends a brawl, so what releases them is the goal losing its threat and `AiThinkSystem` picking another.
 * See [TownsfolkDomain.SHELTERED] for why the effect below is never written back.
 */
class ShelterAtDoorActionTemplate : ActionTemplate {
  override val id = "shelterAtDoor"

  override fun ground(state: WorldState): List<Action> {
    state.get(TownsfolkDomain.SHELTER_DOOR) ?: return emptyList()

    return listOf(
      Action(
        name = "shelterAtDoor",
        preconditions = listOf(Precondition { atShelter(it) }),
        effects = listOf(Effects.set(TownsfolkDomain.SHELTERED, true)),
        cost = { 1f },
        behavior = { StandStill },
      )
    )
  }

  private fun atShelter(state: WorldState): Boolean {
    val door = state.get(TownsfolkDomain.SHELTER_DOOR) ?: return false
    val position = state.get(TownsfolkDomain.POSITION) ?: return false
    return position.distance(door) <= TownsfolkDomain.DOORSTEP_RADIUS
  }
}
