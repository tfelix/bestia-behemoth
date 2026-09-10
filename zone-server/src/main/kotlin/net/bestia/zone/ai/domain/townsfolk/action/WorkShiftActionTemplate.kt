package net.bestia.zone.ai.domain.townsfolk.action

import net.bestia.zone.ai.bt.UntilHour
import net.bestia.zone.ai.bt.leaves.Labour
import net.bestia.zone.ai.bt.leaves.StandStill
import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionTemplate
import net.bestia.zone.ai.core.behavior.BtContext
import net.bestia.zone.ai.core.effect.Effects
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.domain.townsfolk.TownsfolkProduction
import net.bestia.zone.ai.perception.SettlementWork
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.ecs.spawn.townsfolk.TownsfolkIdentity
import net.bestia.zone.economy.Trade

/**
 * Stands the shift out at the post, and does the trade's work while standing it.
 *
 * The precondition is what makes going to work a *journey* the planner finds - from anywhere else the
 * search chains `goToWork -> workShift` - exactly as the doorstep gate does for going to bed.
 *
 * Two shifts, not one. A guard, a priest or a farmhand keeps no shop and has no recipe, so their shift
 * is the time itself; a miller's is a length of time with flour coming out of it. Which one a person
 * gets falls out of whether their occupation names a business the economy models.
 *
 * Running out of grain ends the shift rather than pausing it, and that is deliberate: the goal is gated
 * on [TownsfolkDomain.WORK_SUPPLIED], so a miller with nothing to mill goes and does something else
 * instead of standing at a dead millstone. The leaf writes that belief itself on the way out, because
 * waiting for the next sense pass would spend the intervening seconds replanning the same dead shift.
 */
class WorkShiftActionTemplate(
  private val work: SettlementWork,
  private val production: TownsfolkProduction,
) : ActionTemplate {
  override val id = "workShift"

  override fun ground(state: WorldState): List<Action> {
    val occupation = state.get(TownsfolkDomain.OCCUPATION) ?: return emptyList()
    val shift = occupation.shift ?: return emptyList()
    val today = state.get(TownsfolkDomain.DAY_INDEX) ?: return emptyList()
    val trade = work.tradeOf(occupation.businessType)

    return listOf(
      Action(
        name = "workShift",
        preconditions = listOf(Precondition { atPost(it) }),
        effects = listOf(Effects.set(TownsfolkDomain.WORKED_ON_DAY, today)),
        cost = { 3f },
        behavior = { UntilHour(shift, if (trade == null) StandStill else labourAt(trade, today)) },
      )
    )
  }

  private fun labourAt(trade: Trade, today: Long): Labour {
    return Labour(
      secondsPerUnit = SECONDS_PER_UNIT,
      canBegin = { context -> canBegin(context, trade) },
      onFinished = { context ->
        settlementOf(context)?.let { production.record(it, trade.produces, today) }
      },
    )
  }

  private fun canBegin(context: BtContext, trade: Trade): Boolean {
    val settlement = settlementOf(context) ?: return false
    if (work.canSupply(settlement, trade)) return true

    context.memory.set(TownsfolkDomain.WORK_SUPPLIED, false, TownsfolkDomain.WORK_SUPPLIED.retain)

    return false
  }

  private fun settlementOf(context: BtContext): Int? {
    val townsfolk = context.world.get(context.entityId, Townsfolk::class) ?: return null

    return TownsfolkIdentity.settlementOf(townsfolk.identity)
  }

  private fun atPost(state: WorldState): Boolean {
    val work = state.get(TownsfolkDomain.WORK_POSITION) ?: return false
    val position = state.get(TownsfolkDomain.POSITION) ?: return false
    return position.distance(work) <= TownsfolkDomain.DOORSTEP_RADIUS
  }

  private companion object {
    /**
     * How long one piece of work takes.
     *
     * Chosen to read as work from across the square rather than to match the ledger's own rate, because
     * nothing consumes this output - see [TownsfolkProduction]. Long enough that a passer-by sees a
     * craftsman at a bench and not a blur.
     */
    const val SECONDS_PER_UNIT = 30f
  }
}
