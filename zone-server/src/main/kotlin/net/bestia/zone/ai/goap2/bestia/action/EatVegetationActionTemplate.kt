package net.bestia.zone.ai.goap2.bestia.action

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * Only grounds while standing on the nearest known vegetation spot (see [WalkToVegetationActionTemplate]
 * for closing that distance first), and removes that spot from memory once eaten to model it being
 * foraged out for a while.
 */
class EatVegetationActionTemplate : ActionTemplate {
  override val id = "eatVegetation"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val spots = state.get(BestiaDomain.KNOWN_VEGETATION) ?: return emptyList()
    val nearest = spots.minByOrNull { it.position.distance(position) } ?: return emptyList()
    if (nearest.position.distance(position) > BestiaDomain.ARRIVAL_RADIUS) return emptyList()

    return listOf(
      Action(
        name = "eatVegetation",
        effects = listOf(
          Effects.set(BestiaDomain.HUNGER, 5),
          Effects.modify(BestiaDomain.KNOWN_VEGETATION) { current -> current.orEmpty() - nearest },
        ),
        cost = { 2f },
      )
    )
  }
}
