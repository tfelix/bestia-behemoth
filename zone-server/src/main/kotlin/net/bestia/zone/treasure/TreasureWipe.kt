package net.bestia.zone.treasure

import net.bestia.zone.treasure.persistence.DugTreasureCacheRepository
import net.bestia.zone.treasure.persistence.RelicRecordRepository
import net.bestia.zone.treasure.persistence.TreasureMapRecordRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/**
 * The dug caches and map circles of a world that is being replaced. The trenches live in the chunk edits, wiped
 * too; a map of the old world keeps its copy but shows no circle, and a relic loses the name of a chronicle
 * that is gone.
 */
@Component
class TreasureWipe(
  private val repository: DugTreasureCacheRepository,
  private val maps: TreasureMapRecordRepository,
  private val relics: RelicRecordRepository,
) : WorldScopedData {

  override fun wipe() {
    repository.deleteAll()
    maps.deleteAll()
    relics.deleteAll()
  }
}
