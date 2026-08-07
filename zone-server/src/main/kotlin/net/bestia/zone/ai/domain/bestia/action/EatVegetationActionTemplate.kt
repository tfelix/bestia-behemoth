package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Only grounds while standing on the nearest known vegetation spot (see [WalkToVegetationActionTemplate]
 * for closing that distance first), and removes that spot from memory once eaten to model it being
 * foraged out for a while.
 *
 * Because [BestiaDomain.KNOWN_VEGETATION] is team-scoped, forgetting the spot propagates to the whole
 * pack — one wolf grazing it out stops its packmates walking there next.
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
        behavior = { Wait(EAT_SECONDS) },
      )
    )
  }

  companion object {
    private const val EAT_SECONDS = 4f
  }
}
