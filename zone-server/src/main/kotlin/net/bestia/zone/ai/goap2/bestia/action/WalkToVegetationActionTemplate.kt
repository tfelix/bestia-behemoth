package net.bestia.zone.ai.goap2.bestia.action

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * Grounds one `walkTo(spot)` action per remembered [net.bestia.zone.ai.goap2.bestia.VegetationMemory]
 * (nearest [maxCandidates] first), so [EatVegetationActionTemplate] only has to worry about eating
 * once the bestia is already standing on one.
 */
class WalkToVegetationActionTemplate(private val maxCandidates: Int = 3) : ActionTemplate {
  override val id = "walkToVegetation"

  override fun ground(state: WorldState): List<Action> {
    val position = state.get(BestiaDomain.POSITION) ?: return emptyList()
    val spots = state.get(BestiaDomain.KNOWN_VEGETATION) ?: return emptyList()

    return spots
      .filter { it.position.distance(position) > BestiaDomain.ARRIVAL_RADIUS }
      .sortedBy { it.position.distance(position) }
      .take(maxCandidates)
      .map { spot ->
        Action(
          name = "walkToVegetation(${spot.position})",
          effects = listOf(Effects.set(BestiaDomain.POSITION, spot.position)),
          cost = { spot.position.distance(position).toFloat() },
        )
      }
  }
}
