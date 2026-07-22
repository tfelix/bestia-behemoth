package net.bestia.zone.ai.goap2.bestia.action

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.action.ActionTemplate
import net.bestia.zone.ai.goap2.bestia.BestiaDomain
import net.bestia.zone.ai.goap2.effect.Effects
import net.bestia.zone.ai.goap2.state.WorldState

/** Walks straight back to [BestiaDomain.HOME_POSITION], grounding [BestiaDomain.Goals.RETURN_HOME]. */
class ReturnHomeActionTemplate : ActionTemplate {
  override val id = "returnHome"

  override fun ground(state: WorldState): List<Action> {
    val home = state.get(BestiaDomain.HOME_POSITION) ?: return emptyList()

    return listOf(
      Action(
        name = "returnHome",
        effects = listOf(Effects.set(BestiaDomain.POSITION, home)),
        cost = { s -> BestiaDomain.distanceOrMax(s.get(BestiaDomain.POSITION), home).toFloat() },
      )
    )
  }
}
