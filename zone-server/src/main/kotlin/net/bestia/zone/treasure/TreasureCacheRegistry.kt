package net.bestia.zone.treasure

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.worldgen.treasure.TreasureCaches
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.treasure.persistence.DugTreasureCache
import net.bestia.zone.treasure.persistence.DugTreasureCacheRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * The world's treasure caches as players have left them. Where they lie is the world generator's answer; how far
 * each was dug is kept here in memory and written behind. Tick thread only, after [restoreAll].
 */
@Service
class TreasureCacheRegistry(
  private val worldService: WorldService,
  private val repository: DugTreasureCacheRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val caches by lazy { TreasureCaches(worldService.generated) }

  private val dug = HashMap<Long, DugTreasureCache>()

  fun restoreAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} dug treasure caches of another world or pipeline" }
    }

    dug.clear()
    current.associateByTo(dug) { it.cacheId }
    LOG.info { "Restored ${dug.size} dug treasure caches" }
  }

  /** Caches still buried within [radius] metres of the point, nearest first. */
  fun buriedNear(x: Double, y: Double, radius: Double): List<TreasureCache> {
    return caches.near(x, y, radius)
      .filter { dug[it.id]?.opened != true }
      .sortedBy { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
  }

  fun progressOf(cacheId: Long): DugTreasureCache? {
    return dug[cacheId]
  }

  /** The first dig at a cache fixes which way its trench climbs out. */
  fun recordDig(cacheId: Long, dugMetres: Double, towardX: Double, towardY: Double) {
    val row = dug.getOrPut(cacheId) {
      DugTreasureCache(
        cacheId = cacheId,
        towardX = towardX,
        towardY = towardY,
        worldShapeVersion = worldService.record.shapeVersion,
        pipelineVersion = worldService.record.pipelineVersion
      )
    }
    row.dugMetres = dugMetres
    persist(row)
  }

  fun open(cacheId: Long) {
    val row = dug[cacheId] ?: return
    row.opened = true
    persist(row)
  }

  private fun persist(row: DugTreasureCache) {
    val copy = DugTreasureCache(
      row.cacheId, row.dugMetres, row.opened, row.towardX, row.towardY, row.worldShapeVersion, row.pipelineVersion
    )
    asyncJobExecutor.submit(JOB_KEY) {
      repository.save(copy)
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** One key for every write, so two writes of one cache can never land out of order. */
    const val JOB_KEY = "treasure-caches"
  }
}
