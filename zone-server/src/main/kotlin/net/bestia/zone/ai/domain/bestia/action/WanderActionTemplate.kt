package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.Locomotion
import net.bestia.zone.ai.bt.ParallelPolicy
import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.bt.leaves.Wander
import net.bestia.zone.ai.bt.parallel
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Ambles about near home for a while, spending [BestiaDomain.RESTLESSNESS].
 *
 * Grounding it needs no candidate list — there is exactly one way to wander — but it is still a normal
 * planner action rather than the reflexive fallback it used to be, because restlessness gives it a real
 * unsatisfied precondition to work against.
 *
 * The old note warning that `"wander"` must never appear in a profile's action list no longer applies.
 * It was true when wandering set [BestiaDomain.POSITION] to a *random* tile inside the home radius, which
 * let the planner treat it as a free way to satisfy any position-keyed goal (`ReturnHome` especially) by
 * luck of the draw. Wandering now claims only the restlessness it actually spends, so it can compete
 * honestly and cannot masquerade as travel.
 */
class WanderActionTemplate(private val locomotion: Locomotion) : ActionTemplate {
  override val id = "wander"

  override fun ground(state: WorldState): List<Action> {
    val home = state.get(BestiaDomain.HOME_POSITION) ?: return emptyList()
    val radius = state.get(BestiaDomain.WANDER_RADIUS) ?: BestiaDomain.DEFAULT_WANDER_RADIUS

    return listOf(
      Action(
        name = "wander",
        effects = listOf(Effects.set(BestiaDomain.RESTLESSNESS, 0)),
        cost = { 5f },
        behavior = {
          // Wandering has no natural end of its own, so the timer is what ends it: REQUIRE_ONE means the
          // first child to finish wins, the wander leaf never finishes, and the wait does.
          parallel(ParallelPolicy.REQUIRE_ONE) {
            node(Wander(home, locomotion, radius))
            node(Wait(WANDER_SECONDS))
          }
        },
      )
    )
  }

  companion object {
    /** How long one bout of ambling lasts before the bestia reconsiders what to do. */
    private const val WANDER_SECONDS = 6f
  }
}
