package net.bestia.zone.ground

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * What has passed over the ground and where it was headed.
 *
 * ### Tick-thread only, as `GroundWearRegistry` is
 *
 * Written by movement, read by the broadcaster, swept by [GroundStampSystem] - all on the tick thread, so a
 * plain `HashMap` is correct rather than merely convenient.
 *
 * ### Nothing here is persisted, and nothing is residency-scoped either
 *
 * Both are departures from wear, and both follow from what a stamp is for. A print lasts minutes, so a restart
 * losing them costs nothing anybody would notice - where a path worn over days is exactly what must survive.
 *
 * Residency is the more interesting one. Wear refuses to accumulate in a column nobody holds, because wear
 * nobody can be told about is wear nobody can see. Tracks are the opposite: **a creature leaving tracks where
 * nobody is watching is the whole premise of following them afterwards.** So stamps are recorded wherever
 * something walks, bounded by `GroundStampConfig.maxColumns` rather than by who is logged in, and a column is
 * dropped when its last print expires.
 */
@Service
class GroundStampRegistry(
  private val config: GroundStampConfig,
  private val worldService: WorldService,
  private val clock: BestiaClock,
) : GroundStampSource {

  private val byColumn = HashMap<Long, ColumnStamps>()

  private var warnedFull = false

  private val chunkSize: Int get() = worldService.config.chunkSize

  val stampedColumns get() = byColumn.size

  /** Voxels across one chunk column, so a caller can name the column a tile falls in without a second source. */
  val chunkExtent: Long get() = chunkSize.toLong()

  fun stampsOf(columnKey: Long): ColumnStamps? {
    return byColumn[columnKey]
  }

  /**
   * Records that something passed over one tile.
   *
   * @param octant the eight-connected heading, 0 towards +x and counting towards +y
   * @param seed a shape variant; see [MarkingGroundTrample] for what makes one walk differ from the next
   * @param actorId who left it, which is what a tracker asks about afterwards
   * @return the column the print landed in, or null when the store is full and refuses a new column
   */
  fun stamp(
    voxelX: Long,
    voxelY: Long,
    kind: GroundStampKind,
    octant: Int,
    seed: Int,
    actorId: EntityId,
    nowSecond: Long
  ): Long? {
    val size = chunkSize.toLong()
    val columnKey = ColumnKey.of(
      Math.floorDiv(voxelX, size).toInt(),
      Math.floorDiv(voxelY, size).toInt()
    )

    val column = byColumn[columnKey] ?: newColumn(columnKey) ?: return null

    val localX = Math.floorMod(voxelX, size).toInt()
    val localY = Math.floorMod(voxelY, size).toInt()

    column.add(localY * chunkSize + localX, kind, octant, seed, actorId, nowSecond)

    return columnKey
  }

  /**
   * Expires what has faded and drops a column whose last print has gone.
   *
   * Nothing is announced: a client ages its own copy of a print over the same lifetime, so a print passing out
   * of the store needs no message. The store still expires on its own clock because `SpoorService` reads it
   * with no age filter.
   */
  fun sweep(nowSecond: Long) {
    if (byColumn.isEmpty()) return

    val columns = byColumn.values.iterator()

    while (columns.hasNext()) {
      val column = columns.next()

      column.expire(nowSecond, config.footprintTtlSeconds)

      if (column.isEmpty) columns.remove()
    }
  }

  override fun stampsAt(columnKey: Long): ByteArray? {
    val column = byColumn[columnKey] ?: return null

    return column.toBytes(clock.now().absoluteSecond, config.footprintTtlSeconds)
  }

  private fun newColumn(columnKey: Long): ColumnStamps? {
    if (byColumn.size >= config.maxColumns) {
      if (!warnedFull) {
        warnedFull = true
        LOG.warn { "ground stamps are holding ${byColumn.size} columns, the configured maximum; no more until some expire" }
      }
      return null
    }

    warnedFull = false

    return ColumnStamps(config.maxStampsPerColumn).also { byColumn[columnKey] = it }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
