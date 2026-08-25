package net.bestia.zone.world.prop

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.world.WorldService
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
  /**
   * Read for the two version stamps, and read **here** rather than passed in by each caller.
   *
   * A caller supplying them could supply a mismatched pair - the lattice version of this world beside the
   * shape version of another - and that is precisely the class of bug the second guard was added to catch. One
   * bean reads both off one `record`, so the pair is consistent by construction rather than by every call site
   * remembering to make it so.
   */
  private val worldService: WorldService,
) {

  private val byPropId = HashMap<Long, DivergenceEntry>()

  /** Null for a propId with no recorded divergence - most of them, always, for every kind but a felled tree
   *  or a claimed landmark. Also null for `propId == 0` (a not-yet-generated future non-generated source). */
  fun of(propId: Long): DivergenceEntry? = if (propId == 0L) null else byPropId[propId]

  /** [resumeAt] non-null means temporary (a tree with a `regrowSeconds`); null means terminal. */
  fun recordDepletion(propId: Long, kind: StaticEntityKind, resumeAt: Instant?) {
    if (propId == 0L) return

    byPropId[propId] = DivergenceEntry(kind, DivergenceState.DEPLETED, resumeAt)

    // Read here rather than inside the job, so the row is stamped with the world as it was when the prop was
    // depleted and the job stays pure I/O.
    val latticeVersion = worldService.record.pipelineVersion
    val worldShapeVersion = worldService.record.shapeVersion

    asyncJobExecutor.submit(propId) {
      val row = WorldObjectDivergence(
        propId, kind.name, DivergenceState.DEPLETED, latticeVersion, worldShapeVersion
      )
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
   * Boot-time only. Discards (not merely skips) any row disagreeing with the live world on **either** version
   * - see [WorldObjectDivergence]'s own KDoc for the two guards and for why a stale row is deleted rather than
   * left in the table.
   *
   * The two counts are logged separately because they mean different things to whoever is reading the boot
   * log: a lattice mismatch says the build moved, a shape mismatch says the world did.
   */
  fun loadAll() {
    val latticeVersion = worldService.record.pipelineVersion
    val worldShapeVersion = worldService.record.shapeVersion

    val (valid, orphaned) = repository.findAll().partition {
      it.latticeVersion == latticeVersion && it.worldShapeVersion == worldShapeVersion
    }

    valid.forEach { row ->
      byPropId[row.propId] = DivergenceEntry(StaticEntityKind.valueOf(row.kind), row.state, row.resumeAt)
    }

    if (orphaned.isNotEmpty()) {
      repository.deleteAll(orphaned)

      val staleLattice = orphaned.count { it.latticeVersion != latticeVersion }
      val staleShape = orphaned.count { it.worldShapeVersion != worldShapeVersion }

      LOG.warn {
        "${orphaned.size} world-object divergence row(s) do not belong to this world; discarded " +
            "($staleLattice on lattice, pipelineVersion $latticeVersion; " +
            "$staleShape on shape, shapeVersion $worldShapeVersion)"
      }
    }

    LOG.info { "Loaded ${valid.size} world-object divergence row(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
