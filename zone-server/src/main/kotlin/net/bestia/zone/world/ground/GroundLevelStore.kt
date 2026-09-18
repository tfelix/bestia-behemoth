package net.bestia.zone.world.ground

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.WorldService
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * One graded layer's grid: what is held, what is written, and what fades.
 *
 * ### The same store serves every layer that is a level per square metre
 *
 * Worn ground and spilled blood keep the same thing in the same lattice, decay it the same way and persist it
 * through the same row. What they disagree about is entirely the *writer* - a footfall adding a little to one
 * cell against a death soaking a disc of them - so that is what subclasses add, and nothing else.
 *
 * ### Tick-thread only, the convention `ScorchRegistry` documents for its own map
 *
 * Written by whatever marks the ground, read by the broadcaster and by decay, all on the tick thread. So a
 * plain `HashMap` is correct rather than merely convenient - `parallel-systems` is off and there is no second
 * thread to race.
 *
 * ### Resident, not loaded whole
 *
 * `ScorchRegistry.loadAll()` reads every scar at boot, which is right for a handful of rows and wrong here: a
 * shard eventually holds a row for every column anyone has crossed or bled on. Columns are read in when a
 * player first sees them and written out when the last one leaves, following `PlayerStructureRegistry`.
 *
 * ### Writes are coalesced, and that is the real departure
 *
 * Scorch persists on every `burn()` - once per fire. Wear is touched on every footfall of every creature, so
 * the same rule would be a write per step. Rows go out on eviction and on [flushDirty]; a crash costs a few
 * minutes of marks, which is the right trade for something explicitly mid-term.
 *
 * ### Fading costs no writes at all
 *
 * A column's level is aged on read from a stored timestamp, so a mark can fade for a Bestia week without
 * touching the database and costs one delete when it finally goes. The argument `ScorchRegistry` makes for
 * refusing a stored `greenness` column, reached from the other direction.
 */
abstract class GroundLevelStore(
  private val repository: GroundLayerMarkRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val worldService: WorldService,
  private val config: GroundLevelConfig,
  private val clock: BestiaClock,
  final override val layer: GroundLayer,
) : GroundLayerSource {

  private val byColumn = HashMap<Long, MarkedColumn>()

  /**
   * Columns read off the database, waiting to be merged in on the tick thread.
   *
   * [track] runs inside a subscription callback, which is the tick thread, and a JPA read there would block
   * the whole world on a database round trip per column - 121 of them when a player logs in. So the read goes
   * to [AsyncJobExecutor] and its result comes back through here, the shape `GroundFireService.requestIgnition`
   * uses for the same "caller cannot do this here" problem.
   */
  private val loaded = ConcurrentLinkedQueue<Loaded>()

  private class Loaded(val columnKey: Long, val levels: ColumnLevels, val lastDecayedSecond: Long)

  protected val chunkSize: Int get() = worldService.config.chunkSize

  val fadeSeconds: Long get() = config.fadeSeconds

  val flushIntervalSeconds: Float get() = config.flushIntervalSeconds

  val markedColumns get() = byColumn.size

  /** Voxels across one chunk column, so a caller can name the column a tile falls in without a second source. */
  val chunkExtent: Long get() = chunkSize.toLong()

  /** The columns holding this layer, for the decay sweep. A copy, so a sweep may evict while iterating. */
  fun markedKeys(): List<Long> {
    return byColumn.keys.toList()
  }

  fun columnAt(columnKey: Long): MarkedColumn? {
    return byColumn[columnKey]
  }

  /**
   * Adds to one tile.
   *
   * @return true if a level the client would draw changed, so a writer can mark the column dirty without
   *   re-announcing it on every single step
   */
  protected fun mark(voxelX: Long, voxelY: Long, amount: Int, nowSecond: Long): Boolean {
    if (amount <= 0) return false

    val column = byColumn[columnKeyOf(voxelX, voxelY)] ?: return false

    // Before adding, or a cell topped up every second would never age at all and a busy road would be
    // permanent. The order is what makes traffic *hold* a mark open rather than freeze its clock.
    column.ageTo(nowSecond, config.fadeSeconds)

    val size = chunkExtent
    val localX = Math.floorMod(voxelX, size).toInt()
    val localY = Math.floorMod(voxelY, size).toInt()

    val before = column.levels[localX, localY] >= config.visibleThreshold
    val moved = column.levels.add(localX, localY, amount)
    val after = column.levels[localX, localY] >= config.visibleThreshold

    column.dirty = true

    // Crossing the threshold is a change even when the nibble did not move, and the nibble moving below the
    // threshold is not - what is announced is what is drawn, and below the threshold nothing is.
    return before != after || (moved && after)
  }

  /**
   * Which column holds a tile.
   *
   * Here rather than in each writer, because getting it wrong is silent: `Math.floorDiv` is what makes the
   * column left of the origin `-1` rather than `0`, and an integer division would mirror half the world.
   */
  fun columnKeyOf(voxelX: Long, voxelY: Long): Long {
    val size = chunkExtent

    return ColumnKey.of(Math.floorDiv(voxelX, size).toInt(), Math.floorDiv(voxelY, size).toInt())
  }

  /**
   * Starts holding a column. Called when the first player comes to hold its terrain.
   *
   * The column is usable immediately, empty, and whatever was stored for it is merged in when the read
   * returns - so a mark made in the meantime is kept rather than dropped or blocked on. See [loaded].
   */
  fun track(columnKey: Long) {
    if (byColumn.containsKey(columnKey)) return

    if (byColumn.size >= config.maxResidentColumns) {
      LOG.warn { "$layer is holding ${byColumn.size} columns, the configured maximum; not tracking more" }
      return
    }

    byColumn[columnKey] = MarkedColumn(ColumnLevels(chunkSize), nowSecond())

    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    asyncJobExecutor.submit(columnKey) {
      read(columnKey, shapeVersion, pipelineVersion)?.let { loaded.add(it) }
    }
  }

  /**
   * Merges in whatever the database had for columns tracked since the last call.
   *
   * @return the columns whose stored levels just arrived, so a caller can announce them - a player standing on
   *   a path they walked yesterday has been looking at clean ground until this moment
   */
  fun drainLoaded(nowSecond: Long): List<Long> {
    if (loaded.isEmpty()) return emptyList()

    val announced = mutableListOf<Long>()

    while (true) {
      val row = loaded.poll() ?: break

      // Gone already: the player walked straight back out. Nothing to merge into, and nothing to write - the
      // row on disk is still the truth.
      val held = byColumn[row.columnKey] ?: continue

      val restored = MarkedColumn(row.levels, row.lastDecayedSecond)
      restored.ageTo(nowSecond, config.fadeSeconds)

      // Whatever happened while the read was in flight goes on top of what was stored, rather than either one
      // winning: both really happened.
      held.levels.forEachMarked { x, y, level -> restored.levels.add(x, y, level) }
      restored.dirty = held.dirty

      byColumn[row.columnKey] = restored
      if (!restored.isEmpty) announced.add(row.columnKey)
    }

    return announced
  }

  /** Writes a column out and drops it. Called when the last player holding its terrain leaves. */
  fun release(columnKey: Long) {
    val column = byColumn.remove(columnKey) ?: return
    persist(columnKey, column)
  }

  /** Writes out everything changed since the last call. The periodic half of the coalescing. */
  fun flushDirty(): Int {
    var written = 0
    byColumn.forEach { (columnKey, column) ->
      if (!column.dirty) return@forEach
      persist(columnKey, column)
      written++
    }
    return written
  }

  override fun nibblesAt(columnKey: Long): ByteArray? {
    val column = byColumn[columnKey] ?: return null
    if (column.isEmpty) return null

    // Below the threshold nothing is drawn, so a column holding only the thin spread of random wandering sends
    // nothing at all rather than half a kilobyte of zeroes - see GroundWearConfig.visibleThreshold.
    val visible = ColumnLevels(chunkSize)
    var any = false

    column.levels.forEachMarked { x, y, level ->
      if (level >= config.visibleThreshold) {
        visible.add(x, y, level)
        any = true
      }
    }

    return if (any) visible.toNibbles() else null
  }

  protected fun nowSecond(): Long {
    return clock.now().absoluteSecond
  }

  /** Runs off the tick thread. Touches nothing but the repository and the values handed to it. */
  private fun read(columnKey: Long, shapeVersion: Long, pipelineVersion: Long): Loaded? {
    val row = repository.findByIdColumnKey(columnKey).firstOrNull { it.id.layerId == layer.wireId } ?: return null

    if (row.worldShapeVersion != shapeVersion || row.pipelineVersion != pipelineVersion) {
      // Discarded rather than skipped, on `WorldObjectDivergence`'s reasoning: these cells are drawn on
      // whatever terrain now occupies those coordinates, so a stale row is worse than an absent one.
      repository.delete(row)
      return null
    }

    // The chunk size cannot have changed without the shape version changing with it, so this is a corrupt row
    // rather than one from a differently shaped world. Skipped loudly, as ScorchRegistry.loadAll does.
    return runCatching { Loaded(columnKey, ColumnLevels.fromBytes(chunkSize, row.cells), row.lastDecayedSecond) }
      .onFailure { LOG.error { "$layer row for column $columnKey is not a $chunkSize-wide column; ignored" } }
      .getOrNull()
  }

  private fun persist(columnKey: Long, column: MarkedColumn) {
    column.dirty = false

    // Snapshotted on the tick thread. The job runs later and the grid is mutable, so handing it the live
    // object would serialise whatever happened to it by then.
    val cells = column.levels.toBytes()
    val lastDecayedSecond = column.lastDecayedSecond
    val empty = column.isEmpty
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    asyncJobExecutor.submit(columnKey) {
      if (empty) {
        repository.deleteByIdColumnKeyAndIdLayerId(columnKey, layer.wireId)
      } else {
        repository.save(
          GroundLayerMark(
            GroundLayerMark.Key(columnKey, layer.wireId), cells, lastDecayedSecond, shapeVersion, pipelineVersion
          )
        )
      }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
