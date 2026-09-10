package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.UntilHour
import net.bestia.zone.ai.bt.leaves.StandStill
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain

/**
 * Stands about at the gathering spot until it is time for bed.
 *
 * [WorkShiftActionTemplate]'s shape, against the evening instead of the shift: the hour is what ends it,
 * so a plan rebuilt halfway through does not restart the evening and keep somebody out past midnight.
 */
class MingleActionTemplate : ActionTemplate {
  override val id = "mingle"

  override fun ground(state: WorldState): List<Action> {
    val evening = TownsfolkDomain.eveningOf(state.get(TownsfolkDomain.OCCUPATION)) ?: return emptyList()
    val today = state.get(TownsfolkDomain.DAY_INDEX) ?: return emptyList()

    return listOf(
      Action(
        name = "mingle",
        preconditions = listOf(Precondition { TownsfolkDomain.isAtGathering(it) }),
        effects = listOf(Effects.set(TownsfolkDomain.SOCIALISED_ON_DAY, today)),
        cost = { 3f },
        behavior = { UntilHour(evening, StandStill) },
      )
    )
  }
}
