package net.bestia.zone.world.ground

import net.bestia.zone.ecs.movement.GroundTrample
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Turns footfalls into marks on the ground.
 *
 * ### Two marks from one step, and they disagree about where
 *
 * A footfall wears the ground it lands on *and* leaves a print in it, and [TrampleableGround] answers those
 * two questions separately because most ground says yes to only one of them. Snow takes a print and never
 * wears; a hard dirt track compacts further and holds no print worth drawing. Both are looked up, and either
 * may decline.
 *
 * ### The smear is what makes a path a path
 *
 * A step wears the tile under it and, less, the four beside it. Without that an eight-connected walk leaves a
 * one metre staircase of single cells, which reads as a scratch rather than as a trail - real paths are wider
 * than a foot because feet do not land on the same line twice. Prints get no smear: a print landing in five
 * places at once is not a print.
 *
 * ### Ground that does not mark costs one lookup
 *
 * Both centre lookups come off the same cached surface column, and a zero ends each of them, so a creature
 * crossing a road, a cliff or open water pays for the lookup and nothing else. Only ground that actually wears
 * pays for its neighbours - and they are checked individually rather than assumed to match the centre, which
 * is what stops a trail beside a road smearing onto the paving.
 */
@Service
class MarkingGroundTrample(
  private val trampleable: TrampleableGround,
  private val registry: GroundWearRegistry,
  private val stamps: GroundStampRegistry,
  private val overlay: GroundOverlayService,
  private val config: GroundWearConfig,
  private val clock: BestiaClock,
) : GroundTrample {

  override fun steppedOn(entityId: EntityId, fromX: Long, fromY: Long, toX: Long, toY: Long) {
    val now = clock.now().absoluteSecond

    wearUnder(toX, toY, now)
    printOn(entityId, fromX, fromY, toX, toY, now)
  }

  private fun wearUnder(voxelX: Long, voxelY: Long, nowSecond: Long) {
    val underfoot = trampleable.wearAt(voxelX, voxelY)
    if (underfoot <= 0.0) return

    wear(voxelX, voxelY, config.stepWeight * underfoot, nowSecond)

    if (config.neighbourWeight > 0) {
      for ((dx, dy) in NEIGHBOURS) {
        val beside = trampleable.wearAt(voxelX + dx, voxelY + dy)
        if (beside <= 0.0) continue

        wear(voxelX + dx, voxelY + dy, config.neighbourWeight * beside, nowSecond)
      }
    }
  }

  /**
   * Leaves one print, if the ground here holds one.
   *
   * The column is not announced from here. `GroundStampSystem` decides that a tick later against
   * `GroundStampConfig.announceIntervalSeconds`, because otherwise a single walker would re-send a column's
   * whole stamp set several times a second.
   */
  private fun printOn(entityId: EntityId, fromX: Long, fromY: Long, toX: Long, toY: Long, nowSecond: Long) {
    if (trampleable.impressionAt(toX, toY) <= 0.0) return

    stamps.stamp(
      toX,
      toY,
      GroundStampKind.FOOTPRINT,
      octantOf(toX - fromX, toY - fromY),
      seedOf(entityId, toX, toY),
      entityId,
      nowSecond
    )
  }

  /**
   * Wears one cell and announces its column if that changed what a client would draw.
   *
   * Per cell rather than once per step, because the smear reaches across a chunk boundary every thirty-two
   * metres: announcing only the column stepped in would leave the strip beside the seam a step behind, and it
   * would look like the path thinning exactly on the boundary.
   */
  private fun wear(voxelX: Long, voxelY: Long, amount: Double, nowSecond: Long) {
    if (!registry.wear(voxelX, voxelY, amount.toInt(), nowSecond)) return

    overlay.markLayersDirty(ColumnKey.of(chunkOf(voxelX), chunkOf(voxelY)))
  }

  private fun chunkOf(voxel: Long): Int {
    return Math.floorDiv(voxel, registry.chunkExtent).toInt()
  }

  companion object {

    /** Four-connected, not eight: the diagonals are reached by the next step anyway. */
    private val NEIGHBOURS = listOf(1L to 0L, -1L to 0L, 0L to 1L, 0L to -1L)

    /** One round of a 64-bit avalanche, which is all a shape variant needs. */
    private const val MIX = -7046029254386353131L

    /**
     * Which of the eight directions a step went, `0` towards `+x` and counting towards `+y`.
     *
     * Eight is movement's own resolution rather than a simplification - a path is walked on a lattice. A
     * smoother heading, for tracking, comes from aggregating a run of prints by one walker.
     */
    fun octantOf(dx: Long, dy: Long): Int {
      if (dx == 0L && dy == 0L) return 0

      val eighths = Math.round(Math.atan2(dy.toDouble(), dx.toDouble()) / (Math.PI / 4)).toInt()

      return Math.floorMod(eighths, 8)
    }

    /**
     * A shape variant for one print, stable for as long as the print exists.
     *
     * Derived from the walker and the tile rather than counted, so it needs no per-entity state and a print
     * re-sent an hour later is still the same print. It is what the client jitters a print sideways by, so two
     * creatures on one trail leave two lines of tracks instead of one line walked twice.
     *
     * Deliberately **not** an alternating left-right gait: no function of the tile alone can alternate across
     * both straight and diagonal steps, and carrying a per-entity stride counter is not worth what it buys at
     * the distance any of this is seen from.
     */
    fun seedOf(entityId: EntityId, voxelX: Long, voxelY: Long): Int {
      var hash = entityId * 31 + voxelX
      hash = hash * 31 + voxelY
      hash = hash xor (hash ushr 27)
      hash *= MIX
      hash = hash xor (hash ushr 31)

      return (hash and 0xFF).toInt()
    }
  }
}
