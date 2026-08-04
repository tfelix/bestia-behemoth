package net.bestia.worldgen.voxel

import net.bestia.worldgen.geo.VolcanismStage
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.VectorFeature

/**
 * The standing lava of the crater lakes crossing one chunk.
 *
 * [PondWaterSampler] with a different material, and deliberately the same class rather than a parameter on
 * that one: a lava lake is level for the reason a pond is, is stored as an [AreaFeature] for the reason a
 * pond is, and is read back the same way - but it is **not** in the same contest. The pond sampler's whole
 * job is to lose to a higher water surface where two overlap, and lava must never be compared with water at
 * all. Keeping them apart is what stops a future edit from folding lava into the highest-wins line and
 * putting a lake on top of a lava lake. See `ChunkMaterializer.fillColumn` for what happens if it does.
 *
 * There is no flowing counterpart. A lava flow's surface descends along its length the way a river's does,
 * which is what [RiverWaterSampler] exists for - but nothing generates one, because a flow that is still
 * moving is a thing this world has no model of, and a cooled one is rock. Where a flow's *margin* belongs is
 * in the block palette, as obsidian.
 */
class LavaSampler(features: List<VectorFeature>) {

  /** One lava lake, with its surface resolved once out of the column loop. */
  private class Well(val feature: AreaFeature, val surface: Double)

  private val wells: List<Well> = features
    .asSequence()
    .filter { it.kind == FeatureKind.LAVA_POOL }
    .filterIsInstance<AreaFeature>()
    .mapNotNull { pool ->
      // A pool whose table lacks the surface channel is a producer bug, but it must not take chunk
      // generation down with it - skip it and let the invariant harness be the thing that complains. Same
      // treatment, and same reasoning, as a pond missing its surface elevation.
      runCatching { Well(pool, pool.attribute(VolcanismStage.CHANNEL_SURFACE_ELEVATION)) }.getOrNull()
    }
    .toList()

  val isEmpty get() = wells.isEmpty()

  /**
   * Elevation of the lava surface over a column, or [Double.NaN] where no pool covers it.
   *
   * Containment is [AreaFeature.contains], the exact integer test, so the shoreline of a pool is decided
   * identically by every chunk that touches it. That matters more here than it does for water: a band of
   * columns where one chunk thinks it is lava and the next does not is a band where the cap material, the
   * soil depth and the walkability all disagree across a chunk border.
   *
   * Where two pools overlap the higher surface wins, for the pond sampler's reason - the lower one would
   * leave a dry step in the middle of the lake.
   */
  fun surfaceAt(worldX: Double, worldY: Double): Double {
    if (wells.isEmpty()) return Double.NaN

    var highest = Double.NaN
    for (well in wells) {
      if (!well.feature.contains(worldX, worldY)) continue
      if (highest.isNaN() || well.surface > highest) highest = well.surface
    }
    return highest
  }
}
