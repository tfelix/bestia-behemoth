package net.bestia.zone.account.master.status

import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.identity.ecs.Master as MasterComponent
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.core.World
import net.bestia.zone.persistence.EntityWriteBehind
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Spends a bestia master's unspent status points to permanently raise their effort values.
 *
 * On the tick, against the master's components, which are the authority while the master is online. The
 * write goes out through the master's write-behind like every other change to it, so one persister writes
 * the master row and a spend cannot be overwritten by an older snapshot.
 *
 * A point is **not** a flat +1: each step is priced by [EffortValueCostCalculator] off the value being
 * bought, so the same +1 costs more the higher the attribute already is. There is no cap here - the
 * 9 ceiling is a creation-screen rule only.
 */
@Service
class InvestStatusPointService(
  private val effortValueCostCalculator: EffortValueCostCalculator,
  private val writeBehind: EntityWriteBehind,
) {

  /** Prices the whole batch before raising anything, so a refusal leaves the master as it was. */
  fun investStatusPoints(world: World, entityId: EntityId, investments: List<StatusPointInvestment>) {
    val masterId = world.get(entityId, MasterComponent::class)?.masterId ?: throw MasterNotFoundException()
    val base = world.get(entityId, BaseStatusValues::class) ?: throw MasterNotFoundException()
    val points = world.get(entityId, StatusPoints::class) ?: throw MasterNotFoundException()

    var remaining = points.value
    val deltas = LinkedHashMap<StatusAttribute, Int>()

    for (investment in investments) {
      repeat(investment.amount) {
        // Priced against the value this batch has already raised the attribute to: buying two points in a row
        // up past a cost band pays the higher price for the second one.
        val nextValue = base.effortValue(investment.attribute) + (deltas[investment.attribute] ?: 0) + 1
        val cost = effortValueCostCalculator.stepCost(nextValue)

        if (remaining < cost) {
          throw NoStatusPointsAvailableException(masterId)
        }
        remaining -= cost
        deltas.merge(investment.attribute, 1, Int::plus)
      }
    }

    if (deltas.isEmpty()) return

    deltas.forEach { (attribute, amount) -> base.raiseEffortValue(attribute, amount) }
    // Synced so the owner's client can price the next point; nothing else marks it.
    base.markDirty()
    points.value = remaining
    world.add(entityId, IsStatusValueDirty)

    writeBehind.persist(world, listOf(entityId), withStatusEffects = false)
  }
}
