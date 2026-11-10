package net.bestia.zone.treasure

import net.bestia.zone.treasure.persistence.DugTreasureCacheRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** The dug caches of a world that is being replaced. Their trenches live in the chunk edits, wiped too. */
@Component
class TreasureWipe(
  private val repository: DugTreasureCacheRepository,
) : WorldScopedData {

  override fun wipe() {
    repository.deleteAll()
  }
}
