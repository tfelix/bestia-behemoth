package net.bestia.zone.chronicle

import net.bestia.zone.chronicle.persistence.WorldFactRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** The facts continue one world's history and name its records by index. */
@Component
@Order(8)
class WorldFactWipe(private val facts: WorldFactRepository) : WorldScopedData {

  override fun wipe() {
    facts.deleteAllInBatch()
  }
}
