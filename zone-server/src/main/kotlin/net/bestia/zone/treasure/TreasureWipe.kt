package net.bestia.zone.treasure

import net.bestia.zone.treasure.persistence.DugTreasureCacheRepository
import net.bestia.zone.treasure.persistence.TreasureMapRecordRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/**
 * The dug caches and map circles of a world that is being replaced. The trenches live in the chunk edits, wiped
 * too; a map of the old world keeps its copy but shows no circle.
 */
@Component
class TreasureWipe(
  private val repository: DugTreasureCacheRepository,
  private val maps: TreasureMapRecordRepository,
) : WorldScopedData {

  override fun wipe() {
    repository.deleteAll()
    maps.deleteAll()
  }
}
