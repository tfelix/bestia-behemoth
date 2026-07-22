package net.bestia.zone.ai.goap2.bestia.action

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.state.WorldState

/** Always groundable — sleeping anywhere satisfies [BestiaDomain.Goals.SLEEP]. */
class SleepActionTemplate : ActionTemplate {
  override val id = "sleep"

  override fun ground(state: WorldState): List<Action> = listOf(
    Action(
      name = "sleep",
      effects = listOf(Effects.set(BestiaDomain.TIREDNESS, 5)),
      cost = { 3f },
    )
  )
}
