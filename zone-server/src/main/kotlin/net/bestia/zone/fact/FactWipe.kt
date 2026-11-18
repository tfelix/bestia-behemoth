package net.bestia.zone.fact

import net.bestia.zone.fact.persistence.MasterFactRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** A fact names a record by its index in the chronicle, and the next world has another record there. */
@Component
@Order(7)
class FactWipe(private val facts: MasterFactRepository) : WorldScopedData {

  override fun wipe() {
    facts.deleteAllInBatch()
  }
}
