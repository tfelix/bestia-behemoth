package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import org.springframework.stereotype.Service
import java.time.Instant

/** In-memory mirror of one [WorldObjectDivergence] row, for the tick thread to read without a DB hit. */
data class DivergenceEntry(val kind: StaticEntityKind, val state: DivergenceState, val resumeAt: Instant?)

/**
 * Which generated static entities have diverged from what `propsIn()` alone would produce, and the only
 * place that answer is asked.
 *
 * ### Tick-thread only, same convention [WorldObjectResidencyService] documents for its own maps
 *
 * [of] is read, and [recordDepletion]/[evictRegrown] mutate [byPropId], exclusively from
 * [WorldObjectResidencyService.materialise] and `PropDeathDivergenceSystem`, both of which only ever run on
 * the tick thread. [loadAll] runs once at boot, before the tick loop starts. A plain `HashMap` is therefore
 * correct, not merely convenient - there is never a second thread to race against.
 *
 * ### Durable writes without blocking the tick
 *
 * The in-memory map is updated synchronously so [of] is always consistent within the same tick a depletion
 * or regrowth happens; the DB write is handed to [AsyncJobExecutor] keyed on `propId`, whose own KDoc names
 * this exact use case ("too slow for this-must-be-durable-right-away writes like granting a looted item")
 * and whose per-key ordering guarantee means a propId felled, regrown, and felled again can never have its
 * writes land out of order or interleaved.
 */
@Service
class WorldObjectDivergenceRegistry(
  private val repository: WorldObjectDivergenceRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val byPropId = HashMap<Long, DivergenceEntry>()

  /** Null for a propId with no recorded divergence - most of them, always, for every kind but a felled tree
   *  or a claimed landmark. Also null for `propId == 0` (a not-yet-generated future non-generated source). */
  fun of(propId: Long): DivergenceEntry? = if (propId == 0L) null else byPropId[propId]

  /** [resumeAt] non-null means temporary (a tree with a `regrowSeconds`); null means terminal. */
  fun recordDepletion(propId: Long, kind: StaticEntityKind, latticeVersion: Long, resumeAt: Instant?) {
    if (propId == 0L) return

    byPropId[propId] = DivergenceEntry(kind, DivergenceState.DEPLETED, resumeAt)

    asyncJobExecutor.submit(propId) {
      val row = WorldObjectDivergence(propId, kind.name, DivergenceState.DEPLETED, latticeVersion)
      row.resumeAt = resumeAt
      repository.save(row)
    }
  }

  /** A `resumeAt`-passed entry has grown back; forget it so `materialise()` emits it again. */
  fun evictRegrown(propId: Long) {
    byPropId.remove(propId)
    asyncJobExecutor.submit(propId) { repository.deleteById(propId) }
  }

  /**
   * Boot-time only. Discards (not merely skips) any row whose `latticeVersion` disagrees with
   * [currentLatticeVersion] - see [WorldObjectDivergence]'s own KDoc for why a stale row is deleted rather
   * than left in the table.
   */
  fun loadAll(currentLatticeVersion: Long) {
    val (valid, orphaned) = repository.findAll().partition { it.latticeVersion == currentLatticeVersion }

    valid.forEach { row ->
      byPropId[row.propId] = DivergenceEntry(StaticEntityKind.valueOf(row.kind), row.state, row.resumeAt)
    }

    if (orphaned.isNotEmpty()) {
      repository.deleteAll(orphaned)
      LOG.warn {
        "${orphaned.size} world-object divergence row(s) predate this world's current lattice " +
            "(pipelineVersion $currentLatticeVersion); discarded"
      }
    }

    LOG.info { "Loaded ${valid.size} world-object divergence row(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
