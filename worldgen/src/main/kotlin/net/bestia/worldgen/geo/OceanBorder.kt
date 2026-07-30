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
   * ### Two things at once, and it needs both
   *
   * A *blend* towards deep water, so the transition is continuous in value and slope and there is no step for
   * erosion to turn into an escarpment. And a *ceiling* inside the margin proper, so that the margin is
   * genuinely underwater rather than merely pulled towards being underwater.
   *
   * The first version had only the blend, reaching the natural elevation exactly at the margin's inner
   * boundary - and that cannot guarantee water, because the natural elevation there is the interior and the
   * interior is land. Measured, a cell a thousand metres inside the margin kept about two thirds of its
   * height, so any bedrock above a couple of hundred metres stayed dry and every world had a strip of land at
   * its own seam. `Invariants.checkOceanBorderIsOcean` says so in one line, and had never been registered.
   *
   * So the blend now runs out over [BLEND_SHARE] times the margin *beyond* it - the terrain rises out of the
   * water over a coastal shelf rather than at the margin's edge - and inside the margin a smoothly rising
   * ceiling holds the ground under the waterline. Where the two cross there is a crease in the slope and no
   * step, which is the same thing every `MIN`-blended feature in the pipeline has.
   */
  fun applyTo(elevation: Grid, seaLevel: Double) {
    if (!isEnabled) return

    val target = seaLevel - depthBelowSeaLevel
    val shelf = seaLevel - SHELF_DEPTH
    val blendEnd = marginMetres * (1.0 + BLEND_SHARE)

    for (i in elevation.data.indices) {
      val distance = distanceToEdge(worldXOf(i), worldYOf(i))
      if (distance >= blendEnd) continue

      val inwards = PolylineFeature.smoothstep((distance / blendEnd).coerceIn(0.0, 1.0))
      // Towards the target rather than clamped to it: a natural trench deeper than the target is pulled up
      // instead of down, which is harmless - it is still ocean - and keeps this a single continuous blend.
      var height = target + (elevation.data[i] - target) * inwards

      if (distance < marginMetres) {
        val toShelf = PolylineFeature.smoothstep((distance / marginMetres).coerceIn(0.0, 1.0))
        height = minOf(height, target + (shelf - target) * toShelf)
      }

      elevation.data[i] = height
    }
  }

  companion object {

    /**
     * How far past the margin the blend runs out, as a share of the margin's own width.
     *
     * This is the coastal shelf. It is what lets the ceiling inside the margin be a hard guarantee without
     * putting a cliff at the margin's inner edge: the ground comes out of the water over this band instead of
     * at a line.
     */
    const val BLEND_SHARE = 1.0

    /** Metres below sea level the margin is held at its inner edge. Shallow water, but unambiguously water. */
    const val SHELF_DEPTH = 12.0

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
