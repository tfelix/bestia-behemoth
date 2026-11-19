package net.bestia.zone.prose

import net.bestia.zone.prose.persistence.TextJobBudgetRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** A new world gets its own time to be written. */
@Component
@Order(10)
class TextJobWipe(private val budgets: TextJobBudgetRepository) : WorldScopedData {

  override fun wipe() {
    budgets.deleteAllInBatch()
  }
}
