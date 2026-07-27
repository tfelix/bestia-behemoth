package net.bestia.worldgen.voxel

import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature

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
class RiverWaterSampler(features: List<VectorFeature>) {

  /** One river's geometry with its channel-attribute indices resolved once, out of the column loop. */
  private class Channel(
    val feature: PolylineFeature,
    val bed: Int,
    val depth: Int,
    val width: Int
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
          bed = river.stations.channel(Profiles.CHANNEL_BED_ELEVATION),
          depth = river.stations.channel(Profiles.CHANNEL_DEPTH),
          width = river.stations.channel(Profiles.CHANNEL_WIDTH)
        )
      }.getOrNull()
    }
    .toList()

  val isEmpty get() = channels.isEmpty()

  /**
   * Elevation of the river water surface over a column, or [Double.NaN] where no channel covers it.
   *
   * The surface sits [FREEBOARD] of the channel depth below the bank top, so a channel runs nearly full
   * rather than brim full - a river level with its banks is a river in flood, and every river in the
   * world being in flood reads as a mistake.
   */
  fun surfaceAt(worldX: Double, worldY: Double): Double {
    if (channels.isEmpty()) return Double.NaN

    val point = Vec2d(worldX, worldY)
    var highest = Double.NaN

    for (channel in channels) {
      if (!channel.feature.bbox.contains(worldX, worldY)) continue

      val projection = channel.feature.centerline.project(point)
      val halfWidth = channel.feature.stations.sample(channel.width, projection.u) * 0.5
      if (halfWidth <= 0.0 || projection.distance > halfWidth) continue

      val bankTop = channel.feature.stations.sample(channel.bed, projection.u)
      val depth = channel.feature.stations.sample(channel.depth, projection.u)
      val surface = bankTop - depth * FREEBOARD

      // Where two channels overlap - just above a confluence - the higher surface wins, because the
      // lower one would leave a dry step in the middle of the junction pool.
      if (highest.isNaN() || surface > highest) highest = surface
    }

    return highest
  }

  private companion object {
    /** Fraction of the channel depth left between the water surface and the bank top. */
    const val FREEBOARD = 0.25
  }
}
