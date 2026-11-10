package net.bestia.zone.treasure

import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.geometry.Vec3L
import kotlin.math.floor

/**
 * The ground a dig takes out: a hole under the spot with a ramp that climbs out toward the digger, one metre up
 * for each metre across, so anyone can walk down into it and out again.
 *
 * All in voxel units, like [CarveBrush]. [topZ] is the height of the ground above the spot.
 */
class TreasureTrench(
  val spotX: Double,
  val spotY: Double,
  val topZ: Double,
  val towardX: Double,
  val towardY: Double,
) {

  /** What the dig that reaches [depth] takes out. The narrowest bore the client can draw. */
  fun brushTo(depth: Double): CarveBrush {
    val radius = CarveBrush.MIN_RADIUS
    return CarveBrush.capsule(
      spotX, spotY, topZ - depth + radius,
      spotX + towardX * depth, spotY + towardY * depth, topZ + radius,
      radius
    )
  }

  /** The voxel the next metre of digging starts on. */
  fun voxelBelow(depth: Double): Vec3L {
    return Vec3L(floor(spotX).toLong(), floor(spotY).toLong(), floor(topZ - depth - HALF_VOXEL).toLong())
  }

  private companion object {
    const val HALF_VOXEL = 0.5
  }
}
