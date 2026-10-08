package net.bestia.zone.water

import net.bestia.zone.water.persistence.HeldWaterChunkRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** The held water chunks of a world that is being replaced. Their water lives in the chunk edits, wiped too. */
@Component
class WaterWipe(
  private val repository: HeldWaterChunkRepository,
) : WorldScopedData {

  override fun wipe() {
    repository.deleteAll()
  }
}
