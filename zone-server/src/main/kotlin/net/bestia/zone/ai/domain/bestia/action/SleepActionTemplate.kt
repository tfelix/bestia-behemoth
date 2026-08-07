package net.bestia.zone.ai.domain.bestia.action

import net.bestia.zone.ai.bt.leaves.Wait
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain

/** Always groundable — sleeping anywhere satisfies [BestiaDomain.Goals.SLEEP]. */
class SleepActionTemplate : ActionTemplate {
  override val id = "sleep"

  override fun ground(state: WorldState): List<Action> = listOf(
    Action(
      name = "sleep",
      effects = listOf(Effects.set(BestiaDomain.TIREDNESS, 5)),
      cost = { 3f },
      behavior = { Wait(SLEEP_SECONDS) },
    )
  )

  companion object {
    private const val SLEEP_SECONDS = 10f
  }
}
