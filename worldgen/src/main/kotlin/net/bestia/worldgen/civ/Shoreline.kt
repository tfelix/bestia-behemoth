package net.bestia.worldgen.civ

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.VectorFeature

/**
 * Where water is, to the metre: the sea as the map draws its coast - the bicubic elevation below sea level, the
 * test `TerrainRaster` makes - the lake raster, and the lakes and oxbows `PondStage` draws only as features.
 *
 * The lake raster alone, at a kilometre a cell and NaN over the open ocean, could not see a peninsula or the sea
 * at all, which is how fields came to be sown on the water beside a coastal town.
 */
internal class Shoreline(
  private val elevation: FloatLayer,
  private val waterLevel: FloatLayer,
  private val seaLevel: Double,
  features: List<VectorFeature>,
) {

  private val lakes: List<AreaFeature> = features
    .filter { it.kind == FeatureKind.LAKE || it.kind == FeatureKind.OXBOW_LAKE }
    .filterIsInstance<AreaFeature>()

  private val metres = elevation.region.resolution.metresPerCell

  /** Whether [x], [y] in world metres is land: inside the world, above the sea, and in no lake. */
  fun isDry(x: Double, y: Double): Boolean {
    val cellX = Math.floor(x / metres).toInt()
    val cellY = Math.floor(y / metres).toInt()
    if (!elevation.region.contains(cellX, cellY)) return false
    if (elevation.sampleBicubic(x, y) < seaLevel) return false

    val lakeCellX = Math.floor(x / waterLevel.region.resolution.metresPerCell).toInt()
    val lakeCellY = Math.floor(y / waterLevel.region.resolution.metresPerCell).toInt()
    if (waterLevel.region.contains(lakeCellX, lakeCellY) && !waterLevel[lakeCellX, lakeCellY].isNaN()) return false

    return lakes.none { it.bbox.contains(x, y) && it.ring.contains(x, y) }
  }
}
