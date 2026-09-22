package net.bestia.worldgen.voxel

import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.max

/**
 * The water surface of the rivers crossing one chunk.
 *
 * Rivers need their own water source because they are the one body of water whose surface is *not*
 * level. Sea and lakes are horizontal planes and come out of a raster; a river descends continuously
 * along its length, so its surface elevation is a function of position along the channel and can only
 * come from the channel's own station table.
 *
 * Without this a river valley materialises bone dry - a carved channel with nothing in it, which is the
 * most visible way the voxel tier can be wrong, and precisely the thing the vertical slice is supposed
 * to demonstrate.
 *
 * Built per chunk from the features the spatial index returned, like [net.bestia.worldgen.vector.FeatureEvaluator].
 * Every value is a pure function of world position, so two chunks either side of a border put the water
 * at the same height without consulting each other.
 */
class RiverWaterSampler(features: List<VectorFeature>, private val voxelSize: Double = 1.0) {

  /** One river's geometry with its channel-attribute indices resolved once, out of the column loop. */
  private class Channel(
    val feature: PolylineFeature,
    val water: Int,
    val width: Int,
    /** -1 where the producer wrote no stream power; the bed then falls back to the ordinary cap. */
    val power: Int
  )

  private val channels: List<Channel> = features
    .asSequence()
    .filter { it.kind == FeatureKind.RIVER_CHANNEL }
    .filterIsInstance<PolylineFeature>()
    .mapNotNull { river ->
      // A river whose stations lack the channel geometry is a producer bug, but it must not take chunk
      // generation down with it - skip it and let the invariant harness be the thing that complains.
      runCatching {
        Channel(
          feature = river,
          water = river.stations.channel(Profiles.CHANNEL_WATER_ELEVATION),
          width = river.stations.channel(Profiles.CHANNEL_WIDTH),
          power = runCatching { river.stations.channel(Profiles.CHANNEL_STREAM_POWER) }.getOrDefault(-1)
        )
      }.getOrNull()
    }
    .toList()

  val isEmpty get() = channels.isEmpty()

  /**
   * Elevation of the river water surface over a column, or [Double.NaN] where no channel covers it.
   *
   * The surface is read straight off the channel's own `water_elevation` station rather than
   * reconstructed from the bed and the depth. It has to be: the pool-and-riffle term varies the depth
   * along the reach, and a pool must deepen the bed under a *level* surface rather than dip the surface
   * itself, which is exactly what subtracting a varying depth here would do.
   *
   * ### Why the lateral reach is wider than the wetted half-width
   *
   * It used to be exactly the half-width, and that was wrong in the one direction that shows. **Over-reach
   * is self-correcting and under-reach is not.** Outside the bank line the ground stands at or above the
   * bank top, so `ChunkMaterializer` finds a non-positive water depth there and writes nothing however
   * far this reaches. Under-reach leaves a ring of columns whose ground genuinely *is* below the water
   * line and which no sampler claims - a dry rim around a full channel, and a hard edge where the profile's
   * own bank wobble pushed the real bank outside the nominal half-width. [BANK_REACH_SHARE] covers that
   * wobble, which is a share of the width itself.
   */
  fun surfaceAt(worldX: Double, worldY: Double): Double {
    if (channels.isEmpty()) return Double.NaN

    val point = Vec2d(worldX, worldY)
    var highest = Double.NaN

    for (channel in channels) {
      if (!channel.feature.bbox.contains(worldX, worldY)) continue

      val projection = channel.feature.centerline.project(point)
      val width = channel.feature.stations.sample(channel.width, projection.u)
      val halfWidth = width * 0.5
      if (halfWidth <= 0.0) continue
      if (projection.distance > halfWidth + max(voxelSize, width * BANK_REACH_SHARE)) continue

      val surface = channel.feature.stations.sample(channel.water, projection.u)

      // Where two channels overlap - just above a confluence - the higher surface wins, because the
      // lower one would leave a dry step in the middle of the junction pool.
      if (highest.isNaN() || surface > highest) highest = surface
    }

    return highest
  }

  /**
   * Unit stream power over a column in W/m2, or [Double.NaN] where no channel claims it.
   *
   * Separate from [surfaceAt] rather than returned alongside it, because the caller only wants it for
   * the columns that came back wet - which is a small share of any chunk that has a river in it at all,
   * and none of one that does not. Uses the wetted half-width with no bank reach: the bed material is a
   * fact about the channel floor, and the rim of columns [surfaceAt] deliberately over-reaches into is
   * bank rather than bed.
   */
  fun powerAt(worldX: Double, worldY: Double): Double {
    if (channels.isEmpty()) return Double.NaN

    val point = Vec2d(worldX, worldY)
    var strongest = Double.NaN

    for (channel in channels) {
      if (channel.power < 0) continue
      if (!channel.feature.bbox.contains(worldX, worldY)) continue

      val projection = channel.feature.centerline.project(point)
      val halfWidth = channel.feature.stations.sample(channel.width, projection.u) * 0.5
      if (halfWidth <= 0.0 || projection.distance > halfWidth) continue

      // The livelier channel wins where two overlap, for `surfaceAt`'s reason turned the other way up:
      // just above a confluence the coarser bed is the one that survives the flow of both.
      val power = channel.feature.stations.sample(channel.power, projection.u)
      if (strongest.isNaN() || power > strongest) strongest = power
    }

    return strongest
  }

  private companion object {
    /**
     * Extra lateral reach beyond the wetted half-width, as a share of the channel width.
     *
     * Sized to cover `HydrologyParams.bankRoughness` (0.12 of the width), which moves the real bank in
     * and out around the nominal line. Floored at a voxel by the call site, because a share of a 3 m
     * channel is less than the grid can express.
     */
    const val BANK_REACH_SHARE = 0.15
  }
}
