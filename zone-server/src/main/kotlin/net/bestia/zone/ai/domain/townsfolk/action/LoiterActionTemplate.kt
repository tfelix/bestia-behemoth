package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.ParallelPolicy
import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.bt.leaves.Wander
import net.bestia.zone.ai.bt.parallel
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * A few paces about near home, spending [TownsfolkDomain.RESTLESSNESS].
 *
 * The same shape as the creatures' wandering and for the same reason: ambling has no end of its own, so
 * the timer is what ends it and the bout length is what decides how long a townsperson keeps at it while
 * nothing better applies.
 */
class LoiterActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "loiter"

  override fun ground(state: WorldState): List<Action> {
    val home = state.get(TownsfolkDomain.HOME_POSITION) ?: return emptyList()
    val radius = state.get(TownsfolkDomain.WANDER_RADIUS) ?: TownsfolkDomain.DEFAULT_LOITER_RADIUS

    return listOf(
      Action(
        name = "loiter",
        effects = listOf(Effects.set(TownsfolkDomain.RESTLESSNESS, 0)),
        cost = { 5f },
        behavior = {
          parallel(ParallelPolicy.REQUIRE_ONE) {
            node(Wander(home, locomotion, radius))
            node(Wait(LOITER_SECONDS))
          }
        },
      )
    )
  }

  companion object {
    /** Shorter than a creature's bout, but still a few of [Wander]'s legs and the pauses between them. */
    private const val LOITER_SECONDS = 12f
  }
}
