package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.vector.PolylineFeature

/**
 * The ring of forced deep ocean around a world's edge, and the mask that keeps the land-fraction target honest.
 *
 * Exists to hide a seam. A world that wraps east to west has to look the same on both sides of the join, and
 * making the *terrain* match would mean running every stage on a periodic domain - wrapping Voronoi, noise,
 * flow routing and distance transforms, and worst of all vector features whose geometry crosses the seam, which
 * is exactly the single continuous polyline the seam-free design depends on. Open water is the cheap way out:
 * there is nothing there to fail to match.
 *
 * Applied in [TectonicsStage] rather than at the end, so every later stage sees the water. Erosion drains into
 * it, hydrology will not route a river across it, biomes call it ocean and settlement will not build on it -
 * none of which would be true if the margin were stamped on afterwards.
 *
 * The depression is a smoothstep blend towards a deep target rather than a clamp, because a clamp leaves a ring
 * of dead-flat coastline at exactly the margin's inner edge - a perfect circle of shoreline, which is more
 * conspicuous than the seam it was hiding.
 */
class OceanBorder private constructor(
  private val region: CellRegion,
  private val metresPerCell: Double,
  private val gridWidth: Int,
  private val worldWidth: Double,
  private val worldHeight: Double,
  private val marginMetres: Double,
  private val depthBelowSeaLevel: Double
) {

  val isEnabled get() = marginMetres > 0.0

  /** World x of the centre of the cell at flat grid index [i]. */
  private fun worldXOf(i: Int) = (region.minX + i % gridWidth + 0.5) * metresPerCell

  private fun worldYOf(i: Int) = (region.minY + i / gridWidth + 0.5) * metresPerCell

  /** Distance from the nearest world edge in metres. */
  fun distanceToEdge(x: Double, y: Double) = minOf(x, worldWidth - x, y, worldHeight - y)

  /** Whether a position is outside the margin, and so subject to the ordinary land-fraction target. */
  fun isInterior(x: Double, y: Double) = !isEnabled || distanceToEdge(x, y) >= marginMetres

  /** As [isInterior], addressed by flat grid index. */
  fun isInteriorCell(i: Int) = isInterior(worldXOf(i), worldYOf(i))

  /**
   * Pushes the margin below sea level in place.
   *
   * Weight zero at the world edge and one at the margin's inner boundary, so the transition is continuous in
   * both value and slope and there is no step for erosion to turn into an escarpment.
   */
  fun applyTo(elevation: Grid, seaLevel: Double) {
    if (!isEnabled) return

    val target = seaLevel - depthBelowSeaLevel
    for (i in elevation.data.indices) {
      val distance = distanceToEdge(worldXOf(i), worldYOf(i))
      if (distance >= marginMetres) continue

      val inwards = PolylineFeature.smoothstep((distance / marginMetres).coerceIn(0.0, 1.0))
      // Towards the target rather than clamped to it: a natural trench deeper than the target is pulled up
      // instead of down, which is harmless - it is still ocean - and keeps this a single continuous blend.
      elevation.data[i] = target + (elevation.data[i] - target) * inwards
    }
  }

  companion object {

    fun of(
      config: WorldConfig,
      depthBelowSeaLevel: Double,
      region: CellRegion,
      metresPerCell: Double,
      gridWidth: Int
    ) = OceanBorder(
      region = region,
      metresPerCell = metresPerCell,
      gridWidth = gridWidth,
      worldWidth = config.widthMetres,
      worldHeight = config.heightMetres,
      marginMetres = config.oceanBorderMetres,
      depthBelowSeaLevel = depthBelowSeaLevel
    )
  }
}
