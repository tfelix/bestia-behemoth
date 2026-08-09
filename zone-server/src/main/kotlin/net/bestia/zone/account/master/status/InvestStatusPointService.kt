package net.bestia.zone.account.master.status

import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Spends a bestia master's unspent status points to permanently raise their effort values (the
 * persisted `Master` attribute columns, mirrored into [BaseStatusValues] in the world).
 *
 * Simpler than [net.bestia.zone.account.master.skill.MasterSkillTreeService.investSkillPoints] -
 * there's no prerequisite graph to walk, just a per-attribute delta - but follows the same
 * durability rule: the DB write (decremented `Master.statusPoints` + raised attribute columns)
 * commits first, and the ECS world is only mutated **after commit** (see [afterCommit]), so a crash
 * between commit and the periodic snapshot can never refund an already-spent point.
 *
 * A point is **not** a flat +1: each step is priced by [EffortValueCostCalculator] off the value being
 * bought, so the same +1 costs more the higher the attribute already is. There is no cap here - the
 * 9 ceiling is a creation-screen rule only.
 */
@Service
class InvestStatusPointService(
  private val masterRepository: MasterRepository,
  private val world: WorldView,
  private val masterResolver: MasterResolver,
  private val effortValueCostCalculator: EffortValueCostCalculator
) {

  @Transactional
  fun investStatusPoints(masterId: Long, investments: List<StatusPointInvestment>) {
    val master = masterRepository.findByIdOrThrow(masterId)
    val entityId = masterResolver.getEntityIdByMasterId(masterId)
      ?: throw MasterNotFoundException()

    var remainingStatusPoints = world.read { get(entityId, StatusPoints::class)?.value } ?: 0
    val deltas = LinkedHashMap<StatusAttribute, Int>()

    for (investment in investments) {
      repeat(investment.amount) {
        // Priced against the value this batch has already raised the attribute to, not the value it
        // started at - buying two points in a row up past a cost band has to pay the higher price for
        // the second one.
        val nextValue = master.effortValue(investment.attribute) + (deltas[investment.attribute] ?: 0) + 1
        val cost = effortValueCostCalculator.stepCost(nextValue)

        if (remainingStatusPoints < cost) {
          throw NoStatusPointsAvailableException(master.id)
        }
        remainingStatusPoints -= cost
        deltas[investment.attribute] = (deltas[investment.attribute] ?: 0) + 1
      }
    }

    if (deltas.isEmpty()) return

    applyDeltas(master, deltas)
    master.statusPoints = remainingStatusPoints
    masterRepository.save(master)

    afterCommit { syncToEcs(entityId, remainingStatusPoints, deltas) }
  }

  private fun applyDeltas(master: Master, deltas: Map<StatusAttribute, Int>) {
    deltas.forEach { (attribute, amount) ->
      master.setEffortValue(attribute, master.effortValue(attribute) + amount)
    }
  }

  /**
   * The single place [investStatusPoints] mutates the ECS world, once per batch.
   *
   * [remainingStatusPoints] is assigned rather than subtracted: with a cost curve the points spent are
   * no longer the same number as the attribute points gained, and the already-committed value is the
   * authority.
   */
  private fun syncToEcs(entityId: EntityId, remainingStatusPoints: Int, deltas: Map<StatusAttribute, Int>) {
    world.modify(entityId) { id ->
      get(id, BaseStatusValues::class)?.let { base ->
        deltas.forEach { (attribute, amount) ->
          when (attribute) {
            StatusAttribute.STRENGTH -> base.strength += amount
            StatusAttribute.AGILITY -> base.agility += amount
            StatusAttribute.VITALITY -> base.vitality += amount
            StatusAttribute.INTELLIGENCE -> base.intelligence += amount
            StatusAttribute.DEXTERITY -> base.dexterity += amount
            StatusAttribute.WILLPOWER -> base.willpower += amount
          }
        }
        // BaseStatusValues is Dirtyable so the owner's client can re-price the next point; nothing
        // else marks it, since the recalc system only ever reads it.
        base.markDirty()
      }
      get(id, StatusPoints::class)?.let { it.value = remainingStatusPoints }
      add(id, IsStatusValueDirty)
    }
  }

  /**
   * Runs [block] after the current transaction commits, or immediately if there is no active
   * transaction synchronization (e.g. in a unit test calling the service outside a transaction).
   */
  private fun afterCommit(block: () -> Unit) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
        override fun afterCommit() = block()
      })
    } else {
      block()
    }
  }
}
