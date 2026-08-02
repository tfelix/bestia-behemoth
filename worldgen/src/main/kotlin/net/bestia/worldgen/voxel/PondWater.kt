package net.bestia.worldgen.voxel

import net.bestia.worldgen.hydro.LakeChannels
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.VectorFeature

/**
 * The still water of the vector-tier ponds crossing one chunk.
 *
 * A third water source beside the raster's and [RiverWaterSampler]'s, and it needs to be its own thing for
 * the reason `PondStage` exists at all: these ponds are not in the raster and cannot be, so
 * `SurfaceSampler.waterLevelAt` will never report them however finely it is sampled.
 *
 * Where it differs from the river sampler is that a pond **is** level. A river's surface descends along its
 * channel and has to be interpolated from a station table; a pond's surface elevation is one number stored
 * once and read back unchanged, which is also what makes two chunks either side of a shoreline agree without
 * consulting each other - they are reading the same constant, not evaluating the same function.
 *
 * Built per chunk from the features the spatial index returned, like [RiverWaterSampler] and
 * [net.bestia.worldgen.vector.FeatureEvaluator].
 */
class PondWaterSampler(features: List<VectorFeature>) {

  /** One pond, with its surface resolved once out of the column loop. */
  private class Basin(val feature: AreaFeature, val surface: Double)

  private val basins: List<Basin> = features
    .asSequence()
    .filter { it.kind == FeatureKind.LAKE }
    .filterIsInstance<AreaFeature>()
    .mapNotNull { pond ->
      // A pond whose table lacks the surface channel is a producer bug, but it must not take chunk
      // generation down with it - skip it and let the invariant harness be the thing that complains. Same
      // treatment, and same reasoning, as a river channel missing its geometry.
      runCatching { Basin(pond, pond.attribute(LakeChannels.SURFACE_ELEVATION)) }.getOrNull()
    }
    .toList()

  val isEmpty get() = basins.isEmpty()

  /**
   * Elevation of the pond surface over a column, or [Double.NaN] where no pond covers it.
   *
   * Containment is [AreaFeature.contains], which is the exact integer test - so the shoreline is decided
   * identically by every chunk that touches it, and there is no band of columns where one side thinks it is
   * underwater and the other does not.
   *
   * Where two ponds overlap the higher surface wins, for the same reason the river sampler takes the higher
   * of two channels at a confluence: the lower one would leave a dry step in the middle of the water.
   */
  fun surfaceAt(worldX: Double, worldY: Double): Double {
    if (basins.isEmpty()) return Double.NaN

    var highest = Double.NaN
    for (basin in basins) {
      if (!basin.feature.contains(worldX, worldY)) continue
      if (highest.isNaN() || basin.surface > highest) highest = basin.surface
    }
    return highest
  }
}
