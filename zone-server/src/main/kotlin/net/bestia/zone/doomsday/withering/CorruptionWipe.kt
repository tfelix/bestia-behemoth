package net.bestia.zone.doomsday.withering

import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystalRepository
import net.bestia.zone.doomsday.persistence.StoredCorruptionTileRepository
import net.bestia.zone.doomsday.persistence.StoredWitheredCellRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** A new world is not withered. */
@Component
class CorruptionWipe(
  private val field: CorruptionField,
  private val tiles: StoredCorruptionTileRepository,
  private val crystals: StoredCorruptionCrystalRepository,
  private val withered: StoredWitheredCellRepository,
) : WorldScopedData {

  override fun wipe() {
    field.clear()
    withered.deleteAll()
    tiles.deleteAll()
    crystals.deleteAll()
  }
}
