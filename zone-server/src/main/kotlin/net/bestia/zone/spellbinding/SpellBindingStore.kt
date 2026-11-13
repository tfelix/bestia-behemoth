package net.bestia.zone.spellbinding

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.spellbinding.persistence.SpellBindingRepository
import net.bestia.zone.spellbinding.persistence.SpellBindingRow
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/** Writes the `spell_binding` rows off the tick, in order per master, so a quick bind and release cannot swap. */
@Component
class SpellBindingStore(
  private val repository: SpellBindingRepository,
  private val asyncJobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
) {

  fun save(bound: BoundSpell, position: Vec3L?, itemInstanceId: Long?) {
    val row = SpellBindingRow.of(bound, position, itemInstanceId)
    asyncJobs.submit(key = bound.owner.masterId) {
      transactions.executeWithoutResult { repository.save(row) }
    }
  }

  fun delete(bound: BoundSpell) {
    asyncJobs.submit(key = bound.owner.masterId) {
      transactions.executeWithoutResult { repository.deleteById(bound.id) }
    }
  }
}
