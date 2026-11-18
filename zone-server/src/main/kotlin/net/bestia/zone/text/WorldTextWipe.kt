package net.bestia.zone.text

import net.bestia.zone.text.persistence.WorldTextRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** The texts tell the history of the world they were written for. */
@Component
@Order(6)
class WorldTextWipe(private val repository: WorldTextRepository) : WorldScopedData {

  override fun wipe() {
    repository.deleteAllInBatch()
  }
}
