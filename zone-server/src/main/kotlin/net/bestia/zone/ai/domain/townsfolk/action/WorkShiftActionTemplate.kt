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
 * Stands the shift out at the post.
 *
 * The precondition is what makes going to work a *journey* the planner finds - from anywhere else the
 * search chains `goToWork -> workShift` - exactly as the doorstep gate does for going to bed.
 *
 * [StandStill] is the whole of the work for now, and honestly so: nothing is produced until there are
 * recipes to produce it with. What is already real is the shape - a shift is a length of time the
 * townsperson is somewhere, and [UntilHour] is what holds them there against the world clock rather than
 * against a countdown that would restart every time the plan is reconsidered.
 */
class WorkShiftActionTemplate : ActionTemplate {
  override val id = "workShift"

  override fun ground(state: WorldState): List<Action> {
    val shift = state.get(TownsfolkDomain.OCCUPATION)?.shift ?: return emptyList()
    val today = state.get(TownsfolkDomain.DAY_INDEX) ?: return emptyList()

    return listOf(
      Action(
        name = "workShift",
        preconditions = listOf(Precondition { atPost(it) }),
        effects = listOf(Effects.set(TownsfolkDomain.WORKED_ON_DAY, today)),
        cost = { 3f },
        behavior = { UntilHour(shift, StandStill) },
      )
    )
  }

  private fun atPost(state: WorldState): Boolean {
    val work = state.get(TownsfolkDomain.WORK_POSITION) ?: return false
    val position = state.get(TownsfolkDomain.POSITION) ?: return false
    return position.distance(work) <= TownsfolkDomain.DOORSTEP_RADIUS
  }
}
