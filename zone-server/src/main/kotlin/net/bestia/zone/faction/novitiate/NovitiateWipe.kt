package net.bestia.zone.faction.novitiate

import net.bestia.zone.faction.persistence.NovitiateRecordRepository
import net.bestia.zone.world.WorldScopedData
import org.springframework.stereotype.Component

/** A Novitiate names the towns of one world, and its quest goes with that world. */
@Component
class NovitiateWipe(private val records: NovitiateRecordRepository) : WorldScopedData {

  override fun wipe() {
    records.deleteAllInBatch()
  }
}
