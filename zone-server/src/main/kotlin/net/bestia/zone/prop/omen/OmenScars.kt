package net.bestia.zone.prop.omen

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.persistence.OmenScar
import net.bestia.zone.prop.persistence.OmenScarRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Every scar the mana has left, by chunk column, so [OmenScarSource] answers one column with one lookup.
 *
 * Tick-thread only: scars are raised in a deferred block and read by the residency system. Boot loads before
 * the loop starts.
 */
@Service
class OmenScars(
  private val repository: OmenScarRepository,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  class Scar(val cellKey: Long, val kind: StaticEntityKind, val position: Vec3L, val yaw: Float, val variant: Int)

  private val byColumn = HashMap<Long, MutableList<Scar>>()
  private val cells = HashSet<Long>()

  val count: Int get() = cells.size

  fun inColumn(chunkX: Int, chunkY: Int): List<Scar> = byColumn[columnOf(chunkX, chunkY)] ?: emptyList()

  fun hasScar(cellKey: Long): Boolean = cellKey in cells

  /** Records a new scar and writes it off the tick. False when its cell already has one. */
  fun raise(scar: Scar): Boolean {
    if (!cells.add(scar.cellKey)) return false
    index(scar)

    val row = OmenScar(
      scar.cellKey, scar.kind, scar.position.x, scar.position.y, scar.position.z, scar.yaw, scar.variant,
      worldService.record.shapeVersion, worldService.record.pipelineVersion,
    )
    asyncJobExecutor.submit(JOB_KEY) { repository.save(row) }
    return true
  }

  /** Boot only. Deletes the scars of another world. */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} omen scar(s) of another world or pipeline" }
    }

    current.forEach { row ->
      if (cells.add(row.cellKey)) index(Scar(row.cellKey, row.kind, Vec3L(row.x, row.y, row.z), row.yaw, row.variant))
    }
    LOG.info { "Loaded ${cells.size} omen scar(s)" }
  }

  private fun index(scar: Scar) {
    val chunkSize = worldService.config.chunkSize.toLong()
    val column = columnOf(Math.floorDiv(scar.position.x, chunkSize).toInt(), Math.floorDiv(scar.position.y, chunkSize).toInt())
    byColumn.getOrPut(column) { ArrayList() } += scar
  }

  private fun columnOf(chunkX: Int, chunkY: Int): Long = (chunkX.toLong() shl 32) or (chunkY.toLong() and 0xFFFFFFFFL)

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** One key for every write, so they land in order. */
    const val JOB_KEY = "omen-scars"
  }
}
