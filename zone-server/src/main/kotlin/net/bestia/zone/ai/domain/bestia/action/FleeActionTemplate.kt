package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.leaves.FleeFrom
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Breaks off and puts distance between itself and the nearest threat, satisfying
 * [BestiaDomain.Goals.FLEE].
 *
 * The [BestiaDomain.SAFE] effect is a *belief*, and an honest one: the behaviour only reports success
 * once the bestia is genuinely [BestiaDomain.SAFE_DISTANCE] tiles clear of the threat, so the belief is
 * recorded from an observed outcome rather than from having decided to run. Perception clears it again
 * the moment a hostile is back in sight.
 *
 * Grounds only when a threat is actually known — fleeing from nothing is not a plan.
 */
class FleeActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "flee"

  override fun ground(state: WorldState): List<Action> {
    val threat = state.get(BestiaDomain.THREAT_POSITION) ?: return emptyList()

    return listOf(
      Action(
        name = "flee",
        effects = listOf(Effects.set(BestiaDomain.SAFE, true)),
        cost = { 1f },
        behavior = { FleeFrom(threat, locomotion, safeDistance = BestiaDomain.SAFE_DISTANCE) },
      )
    )
  }
}
