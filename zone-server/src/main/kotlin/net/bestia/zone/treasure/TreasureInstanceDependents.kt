package net.bestia.zone.treasure

import net.bestia.zone.item.container.ItemInstanceDependents
import net.bestia.zone.treasure.persistence.RelicRecordRepository
import net.bestia.zone.treasure.persistence.TreasureMapRecordRepository
import org.springframework.stereotype.Component

/** A treasure map's circle and a relic's artifact are rows keyed by the copy, so they go with the copy. */
@Component
class TreasureInstanceDependents(
  private val records: TreasureMapRecordRepository,
  private val registry: TreasureMapRegistry,
  private val relicRecords: RelicRecordRepository,
  private val relics: Relics,
) : ItemInstanceDependents {

  override fun deleteFor(instanceIds: Collection<Long>) {
    records.deleteAllById(instanceIds)
    registry.forget(instanceIds)
    relicRecords.deleteAllById(instanceIds)
    relics.forget(instanceIds)
  }
}
