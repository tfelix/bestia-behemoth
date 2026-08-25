package net.bestia.zone.world.fire

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * One chunk column's scar: the burnt cells, when the fire that made them started, and the regrowth memo.
 *
 * [rainMm] and [integratedThroughSecond] are **not persisted** and must not be. Rain since
 * [burnedAtSecond] is a pure
 * function of `(seed, region, t)` - `WeatherModel` has no state - so this is a memo that cannot go stale,
 * where a stored counter could. See [ScorchRegistry] for the shape of the argument and `WeatherService`'s own
 * KDoc for where it comes from.
 */
class Scar(
  /**
   * The scar as it was burnt, and it **never shrinks**.
   *
   * Regrowth is expressed entirely through [erodeTo], so what is stored stays the original burn and the
   * healing is a function of it. Eroding the stored mask in place instead would compound: the next pass would
   * erode an already-eroded mask by the same number of steps again, and a scar would vanish in minutes.
   */
  val mask: ColumnMask,
  var burnedAtSecond: Long,
) {
  /** Millimetres of rain integrated onto this scar so far. */
  var rainMm: Double = 0.0

  /** Bestia second [rainMm] is integrated up to, so the next pass continues rather than restarting. */
  var integratedThroughSecond: Long = burnedAtSecond

  var erodeSteps: Int = 0
    private set

  /**
   * The cells still burnt: [mask] eroded by however much rain has fallen on it.
   *
   * What every reader wants - the overlay, the fire deciding where it may spread. Cached rather than computed
   * per call because erosion walks the mask once per step and a chunk being announced asks for this.
   */
  var visible: ColumnMask = mask
    private set

  /** Recomputes [visible] when the rain has moved the erosion on. Cheap and idempotent when it has not. */
  fun erodeTo(steps: Int) {
    if (steps == erodeSteps) return
    erodeSteps = steps
    visible = mask.eroded(steps)
  }
}

/**
 * Which ground is burnt, and the only place that answer is asked.
 *
 * ### Tick-thread only, the convention `WorldObjectDivergenceRegistry` documents for its own map
 *
 * Read by the fire and by the overlay, written by the fire and by regrowth, all on the tick thread; [loadAll]
 * runs once at boot before the loop starts. So a plain `HashMap` is correct rather than merely convenient -
 * there is never a second thread to race against.
 *
 * ### Durable writes without blocking the tick
 *
 * The in-memory mask is updated synchronously so a reader inside the same tick sees it; the row goes to
 * [AsyncJobExecutor] keyed on the column, whose per-key ordering means a column burnt, healed, and burnt again
 * cannot have its writes land out of order.
 *
 * ### Healing costs no writes at all
 *
 * A scar's *effective* mask is `stored.eroded(k)` with `k` derived from accumulated rain, recomputed on read
 * and never stored. So a scar shrinking for an hour writes nothing, and only vanishing costs one delete. A
 * stored `greenness` column would have made every regrowth step a write, and would have been persisting
 * something recomputable - which is what `WeatherService` refuses a table for.
 */
@Service
class ScorchRegistry(
  private val repository: ScorchRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
) {

  private val byColumn = HashMap<Long, Scar>()

  private val chunkSize: Int get() = worldService.config.chunkSize

  val scarredColumns get() = byColumn.size

  /** Null for a column with no scar, which is almost all of them almost always. */
  fun scarOf(columnKey: Long): Scar? = byColumn[columnKey]

  /** The columns holding a scar, for regrowth to sweep. A copy, so a sweep may delete while iterating. */
  fun scarredKeys(): List<Long> = byColumn.keys.toList()

  /**
   * Marks cells burnt in one column and stamps the scar with [burnedAt].
   *
   * [burnedAtSecond] is the **fire's** ignition instant, not now - see [ScorchMark]. A column already scarred takes
   * the newer instant, which resets its clock; that is the documented cost of not keeping a timestamp per cell.
   *
   * @return true if any cell was not already burnt, so a caller can skip re-announcing an unchanged column
   */
  fun burn(columnKey: Long, cells: ColumnMask, burnedAtSecond: Long): Boolean {
    val existing = byColumn[columnKey]

    val scar = existing ?: Scar(ColumnMask(chunkSize), burnedAtSecond).also { byColumn[columnKey] = it }

    val before = scar.mask.count
    scar.mask.or(cells)
    val changed = scar.mask.count != before

    if (!changed && existing != null) return false

    if (burnedAtSecond > scar.burnedAtSecond) {
      scar.burnedAtSecond = burnedAtSecond
      // The window moved, so the rain integrated over the old one no longer applies to this scar.
      scar.rainMm = 0.0
      scar.integratedThroughSecond = burnedAtSecond
    }

    save(columnKey, scar)
    return true
  }

  /**
   * Drops a fully healed scar.
   *
   * **The only write regrowth ever makes.** A scar shrinking is a change to `Scar.visible`, which is derived
   * from the stored mask and never written back - so a scar can spend a Bestia week getting smaller without
   * touching the database, and costs exactly one delete when it finally goes.
   */
  fun forget(columnKey: Long) {
    if (byColumn.remove(columnKey) == null) return
    asyncJobExecutor.submit(columnKey) { repository.deleteById(columnKey) }
  }

  private fun save(columnKey: Long, scar: Scar) {
    // Snapshotted on the tick thread. The job runs later and the mask is mutable, so handing it the live object
    // would serialise whatever the fire had done to it by then.
    val bytes = scar.mask.toBytes()
    val burnedAtSecond = scar.burnedAtSecond
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    asyncJobExecutor.submit(columnKey) {
      repository.save(ScorchMark(columnKey, bytes, burnedAtSecond, shapeVersion, pipelineVersion))
    }
  }

  /**
   * Boot-time only. Discards - not merely skips - any row belonging to a different world, on
   * `WorldObjectDivergence`'s reasoning: a mask for ground that no longer exists would be *drawn* on whatever
   * terrain now occupies those coordinates.
   */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    val (valid, orphaned) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    var skipped = 0
    valid.forEach { row ->
      // A mask whose length does not match this world's chunk size is a row from a differently shaped world
      // that the version stamps somehow let through. Skip it loudly rather than throw the boot away.
      runCatching { ColumnMask.fromBytes(chunkSize, row.mask) }
        .onSuccess { byColumn[row.columnKey] = Scar(it, row.burnedAtSecond) }
        .onFailure { skipped++ }
    }

    if (orphaned.isNotEmpty()) {
      repository.deleteAll(orphaned)
      LOG.warn { "${orphaned.size} ground-scorch row(s) do not belong to this world; discarded" }
    }

    if (skipped > 0) {
      LOG.error { "$skipped ground-scorch row(s) are not $chunkSize-wide masks; ignored" }
    }

    LOG.info { "Loaded ${byColumn.size} ground-scorch row(s), ${byColumn.values.sumOf { it.mask.count }} cells" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    fun columnKeyOf(chunkX: Int, chunkY: Int): Long =
      (chunkX.toLong() shl 32) or (chunkY.toLong() and 0xFFFFFFFFL)

    fun chunkXOf(columnKey: Long): Int = (columnKey shr 32).toInt()

    fun chunkYOf(columnKey: Long): Int = columnKey.toInt()
  }
}
