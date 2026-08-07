package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.MoveTo
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Grounds one `walkTo(spot)` action per remembered [net.bestia.zone.ai.domain.bestia.VegetationMemory]
 * (nearest [maxCandidates] first), so [EatVegetationActionTemplate] only has to worry about eating once
 * the bestia is already standing on one.
 *
 * One template, several concrete actions with different costs and *different destinations in their
 * behaviour* — this is the case a parameterless action could not express at all, and the reason the
 * planner grounds against known values rather than searching over positions.
 */
class WalkToVegetationActionTemplate(
  private val locomotion: Locomotion,
  private val maxCandidates: Int = 3,
) : ActionTemplate {
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
          behavior = { MoveTo(spot.position, locomotion, arrivalRadius = BestiaDomain.ARRIVAL_RADIUS) },
        )
      }
  }
}
