package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.leaves.Sleep
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.action.Posture
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Sleeps, but only at the door of one's own house.
 *
 * The precondition is what makes a bedtime a *journey*. `CompositeActionResolver` tests it against each
 * state the search reaches rather than only against the start, so from out in the street the planner finds
 * `goHome -> sleepAtHome` and the townsperson walks home and lies down; from the doorstep it finds
 * `sleepAtHome` alone. Without it, everyone would sleep where dusk caught them.
 *
 * The tiredness effect is a prediction rather than the mechanism - `AiDriveSystem` runs tiredness backwards
 * while the posture says asleep - which is what makes a night broken early still worth something.
 */
class SleepAtHomeActionTemplate : ActionTemplate {
  override val id = "sleepAtHome"

  override fun ground(state: WorldState): List<Action> = listOf(
    Action(
      name = "sleepAtHome",
      preconditions = listOf(Precondition { TownsfolkDomain.isAtHome(it) }),
      effects = listOf(
        Effects.set(TownsfolkDomain.TIREDNESS, 5),
        Effects.set(TownsfolkDomain.RESTED, true),
      ),
      cost = { 3f },
      posture = Posture.SLEEPING,
      behavior = { Sleep(MIN_SLEEP_SECONDS, ::stillSleeping) },
    )
  )

  companion object {
    /** A floor, not a duration: both real reasons to stay in bed are conditions rather than clocks. */
    private const val MIN_SLEEP_SECONDS = 3f

    private fun stillSleeping(context: BtContext): Boolean {
      val hour = context.memory.get(TownsfolkDomain.HOUR_OF_DAY)
      val stillNight = hour != null && TownsfolkDomain.isBedtime(hour)

      return stillNight ||
        (context.memory.get(TownsfolkDomain.TIREDNESS) ?: 0) > TownsfolkDomain.RESTED_TIREDNESS
    }
  }
}
