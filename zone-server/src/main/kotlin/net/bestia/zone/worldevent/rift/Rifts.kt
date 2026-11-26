package net.bestia.zone.worldevent.rift

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.persistence.StoredRiftRepository
import org.springframework.stereotype.Service

/**
 * The rifts still forming or open, held in memory and written out as they change. The director forms them off
 * the tick and the rift system moves them on it, so every method locks.
 */
@Service
class Rifts(
  private val repository: StoredRiftRepository,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val active = LinkedHashMap<Long, StoredRift>()
  private val ended = HashMap<Long, Phase>()

  @Synchronized
  fun active(): List<StoredRift> = active.values.toList()

  /** The phase of rift [id], also after it ended; null for a rift this process never knew. */
  @Synchronized
  fun phaseOf(id: Long): Phase? = active[id]?.phase ?: ended[id] ?: repository.findById(id).orElse(null)?.phase

  /** Forms a knot. Saved at once, because the director stores its id. */
  @Synchronized
  fun formKnot(x: Double, y: Double, provinceIndex: Int, now: Long, opensAt: Long): StoredRift {
    val rift = repository.save(
      StoredRift(
        x = x, y = y, provinceIndex = provinceIndex, formedAtSecond = now, opensAtSecond = opensAt,
        worldShapeVersion = worldService.record.shapeVersion, pipelineVersion = worldService.record.pipelineVersion,
      )
    )
    active[rift.id] = rift
    LOG.info { "A rift knot formed at (${x.toInt()}, ${y.toInt()}) in province $provinceIndex" }
    return rift
  }

  /** Writes [rifts] off the tick, and forgets the ones that ended. */
  @Synchronized
  fun saveAll(rifts: Collection<StoredRift>) {
    val rows = rifts.map(::copyOf)
    rifts.filterNot { it.phase.isActive }.forEach {
      active.remove(it.id)
      ended[it.id] = it.phase
    }
    if (rows.isNotEmpty()) asyncJobExecutor.submit(JOB_KEY) { repository.saveAll(rows) }
  }

  /** Boot only. Deletes the rifts of another world. */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }
    if (stale.isNotEmpty()) repository.deleteAll(stale)

    synchronized(this) { current.filter { it.phase.isActive }.forEach { active[it.id] = it } }
    LOG.info { "Loaded ${active.size} active rift(s)" }
  }

  /** A detached copy, so a write off the tick never reads a rift the tick is changing. */
  private fun copyOf(rift: StoredRift) = StoredRift(
    rift.x, rift.y, rift.provinceIndex, rift.phase, rift.stage, rift.reservoir, rift.formedAtSecond,
    rift.opensAtSecond, rift.collapsesAtSecond, rift.anchorEntityId, rift.anchorSlain, rift.nextAnchorStage,
    rift.sealWindowEndsAtSecond, rift.nextWaveAtSecond, rift.worldShapeVersion, rift.pipelineVersion,
    rift.anchorSpecies,
  ).also { it.id = rift.id }

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val JOB_KEY = "rifts"
  }
}
