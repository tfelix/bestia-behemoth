package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.leaves.Sleep
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.action.Posture
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/**
 * Always groundable — sleeping anywhere satisfies [BestiaDomain.Goals.SLEEP].
 *
 * One action covers both reasons to sleep, and the [Sleep] leaf's predicate is what makes it: a nap lasts
 * until the creature is rested, a night's sleep until the night is over. Splitting them into two actions
 * would have given the planner two identical ways to satisfy one goal.
 *
 * Both desired conditions of that goal are claimed here because both are genuinely true afterwards: the
 * creature is no longer tired, and it has now slept out whatever kept it up. Claiming only the first would
 * leave the goal unsatisfiable at night, and the search would report no plan.
 *
 * The tiredness effect is a *prediction* rather than the mechanism. `AiDriveSystem` runs tiredness backwards
 * for as long as the posture says asleep, so the recovery has already happened by the time this is written
 * back — which is what makes an interrupted night still worth something.
 */
class SleepActionTemplate : ActionTemplate {
  override val id = "sleep"

  override fun ground(state: WorldState): List<Action> = listOf(
    Action(
      name = "sleep",
      effects = listOf(
        Effects.set(BestiaDomain.TIREDNESS, 5),
        Effects.set(BestiaDomain.RESTED, true),
      ),
      cost = { 3f },
      posture = Posture.SLEEPING,
      behavior = { Sleep(MIN_SLEEP_SECONDS, ::stillSleeping) },
    )
  )

  companion object {
    /**
     * A floor, not a duration. Both real reasons to keep sleeping are conditions rather than clocks; this
     * only stops a sleep that satisfies neither from being a single-tick no-op the creature never visibly did.
     */
    private const val MIN_SLEEP_SECONDS = 3f

    private fun stillSleeping(context: BtContext): Boolean =
      BestiaDomain.isRestingPhase(context.memory) ||
        (context.memory.get(BestiaDomain.TIREDNESS) ?: 0) > BestiaDomain.RESTED_TIREDNESS
  }
}
