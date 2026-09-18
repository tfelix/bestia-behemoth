package net.bestia.zone.world.ground

import net.bestia.zone.ecs.movement.GroundTrample
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * Turns footfalls into worn ground.
 *
 * ### The smear is what makes a path a path
 *
 * A step wears the tile under it and, less, the four beside it. Without that an eight-connected walk leaves a
 * one metre staircase of single cells, which reads as a scratch rather than as a trail - real paths are wider
 * than a foot because feet do not land on the same line twice.
 *
 * ### Ground that does not wear costs one lookup
 *
 * The centre tile is checked first and a zero ends it, so a creature crossing a road, a cliff or open water
 * pays a single cached surface lookup and nothing else. Only ground that actually takes a path pays for its
 * neighbours - and they are checked individually rather than assumed to match the centre, which is what stops
 * a trail beside a road smearing onto the paving.
 */
@Service
class WearingGroundTrample(
  private val trampleable: TrampleableGround,
  private val registry: GroundWearRegistry,
  private val overlay: GroundOverlayService,
  private val config: GroundWearConfig,
  private val clock: BestiaClock,
) : GroundTrample {

  override fun steppedOn(voxelX: Long, voxelY: Long) {
    val underfoot = trampleable.wearAt(voxelX, voxelY)
    if (underfoot <= 0.0) return

    val now = clock.now().absoluteSecond
    wear(voxelX, voxelY, config.stepWeight * underfoot, now)

    if (config.neighbourWeight > 0) {
      for ((dx, dy) in NEIGHBOURS) {
        val beside = trampleable.wearAt(voxelX + dx, voxelY + dy)
        if (beside <= 0.0) continue

        wear(voxelX + dx, voxelY + dy, config.neighbourWeight * beside, now)
      }
    }
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

  private companion object {

    /** Four-connected, not eight: the diagonals are reached by the next step anyway. */
    val NEIGHBOURS = listOf(1L to 0L, -1L to 0L, 0L to 1L, 0L to -1L)
  }
}
