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
 * Spends a bestia master's unspent status points to permanently raise their base status values.
 * Simpler than [net.bestia.zone.account.master.skill.MasterSkillTreeService.investSkillPoints] -
 * there's no prerequisite graph to walk, just a flat per-attribute delta - but follows the same
 * durability rule: the DB write (decremented `Master.statusPoints` + raised base attribute
 * columns) commits first, and the ECS world is only mutated **after commit** (see [afterCommit]),
 * so a crash between commit and the periodic snapshot can never refund an already-spent point.
 */
@Service
class InvestStatusPointService(
  private val masterRepository: MasterRepository,
  private val world: WorldView,
  private val masterResolver: MasterResolver
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
        if (remainingStatusPoints <= 0) {
          throw NoStatusPointsAvailableException(master.id)
        }
        remainingStatusPoints -= 1
        deltas[investment.attribute] = (deltas[investment.attribute] ?: 0) + 1
      }
    }

    if (deltas.isEmpty()) return

    applyDeltas(master, deltas)
    master.statusPoints = remainingStatusPoints
    masterRepository.save(master)

    afterCommit { syncToEcs(entityId, deltas) }
  }

  private fun applyDeltas(master: Master, deltas: Map<StatusAttribute, Int>) {
    deltas.forEach { (attribute, amount) ->
      when (attribute) {
        StatusAttribute.STRENGTH -> master.strength += amount
        StatusAttribute.AGILITY -> master.agility += amount
        StatusAttribute.VITALITY -> master.vitality += amount
        StatusAttribute.INTELLIGENCE -> master.intelligence += amount
        StatusAttribute.DEXTERITY -> master.dexterity += amount
        StatusAttribute.WILLPOWER -> master.willpower += amount
      }
    }
  }

  /** The single place [investStatusPoints] mutates the ECS world, once per batch. */
  private fun syncToEcs(entityId: EntityId, deltas: Map<StatusAttribute, Int>) {
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
      }
      get(id, StatusPoints::class)?.let { it.value -= deltas.values.sum() }
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
