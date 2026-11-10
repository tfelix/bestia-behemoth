package net.bestia.zone.treasure

import net.bestia.zone.item.container.ItemInstanceDependents
import net.bestia.zone.treasure.persistence.TreasureMapRecordRepository
import org.springframework.stereotype.Component

/** A treasure map's circle is a row keyed by its copy, so it goes with the copy. */
@Component
class TreasureMapInstanceDependents(
  private val records: TreasureMapRecordRepository,
  private val registry: TreasureMapRegistry,
) : ItemInstanceDependents {

  override fun deleteFor(instanceIds: Collection<Long>) {
    records.deleteAllById(instanceIds)
    registry.forget(instanceIds)
  }
}
