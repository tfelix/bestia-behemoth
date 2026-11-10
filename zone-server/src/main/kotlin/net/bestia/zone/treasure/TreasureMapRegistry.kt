package net.bestia.zone.treasure

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.ecs.MapCircles
import net.bestia.zone.treasure.persistence.TreasureMapRecord
import net.bestia.zone.treasure.persistence.TreasureMapRecordRepository
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Every treasure map copy that still points at a buried cache, in memory. Read on the tick by every inventory
 * sync, written by the job that draws a map, so it is a concurrent map.
 */
@Service
class TreasureMapRegistry(
  private val repository: TreasureMapRecordRepository,
) : MapCircles {

  private val byCopy = ConcurrentHashMap<Long, TreasureMapRecord>()

  init {
    MapCircles.installed = this
  }

  fun restoreAll() {
    byCopy.clear()
    repository.findAll().associateByTo(byCopy) { it.itemInstanceId }
    LOG.info { "Restored ${byCopy.size} treasure maps" }
  }

  override fun of(uniqueId: Long): MapCircles.Circle? {
    val record = byCopy[uniqueId] ?: return null
    return MapCircles.Circle(record.centreX, record.centreY, record.radius)
  }

  fun put(record: TreasureMapRecord) {
    byCopy[record.itemInstanceId] = record
  }

  /** Forgets every copy that points at the cache, and answers which they were. */
  fun takeAllOf(cacheId: Long): List<Long> {
    val copies = byCopy.values.filter { it.cacheId == cacheId }.map { it.itemInstanceId }
    copies.forEach(byCopy::remove)
    return copies
  }

  fun forget(copies: Collection<Long>) {
    copies.forEach(byCopy::remove)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
