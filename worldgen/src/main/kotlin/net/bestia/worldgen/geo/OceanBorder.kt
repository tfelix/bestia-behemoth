package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.vector.PolylineFeature
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
  private val depthBelowSeaLevel: Double,
  private val wobbleMetres: Double,
  private val wobbleSeed: Long
) {

  val isEnabled get() = marginMetres > 0.0

  /** World x of the centre of the cell at flat grid index [i]. */
  private fun worldXOf(i: Int) = (region.minX + i % gridWidth + 0.5) * metresPerCell

  private fun worldYOf(i: Int) = (region.minY + i / gridWidth + 0.5) * metresPerCell

  /**
   * Distance from the nearest world edge in metres, with the edge itself wandering inland.
   *
   * ### Why the plain version had to go
   *
   * `minOf(x, W - x, y, H - y)` is the distance to a rectangle, and its contours *are* rectangles: four
   * straight lines parallel to the world's edges, meeting at square corners with a 45-degree crease along each
   * diagonal where the `min` switches terms. Everything this class does is a function of that one number, so
   * however smooth the blend below is, the shoreline it produces can only be a rectangle - and it was one,
   * conspicuously, on every world. The plate boundaries are domain-warped Voronoi and produce nothing straight;
   * the straightness was entirely this.
   *
   * ### The rule that keeps it safe
   *
   * `Invariants.checkOceanBorderIsOcean` asserts that nothing within [marginMetres] of an edge is above sea
   * level, and it measures that with [WorldWrap]'s own *unperturbed* distance. So:
   *
   * > this may only ever return **less** than the true `minOf(x, W - x, y, H - y)`.
   *
   * Every term below is a true distance minus a non-negative wobble, and the smooth minimum is itself never
   * greater than `min`, so the property holds by construction rather than by tuning. Wherever the true distance
   * is inside the margin the effective distance is too, the ceiling in [applyTo] applies, and the cell is
   * underwater. What the wobble moves is the *outer* edge of the drowning - inland, never seaward.
   */
  fun distanceToEdge(x: Double, y: Double): Double {
    val south = y - wobbleAlong(SOUTH_SALT, x / worldWidth)
    val north = (worldHeight - y) - wobbleAlong(NORTH_SALT, x / worldWidth)
    val west = x - wobbleAlong(WEST_SALT, y / worldHeight)
    val east = (worldWidth - x) - wobbleAlong(EAST_SALT, y / worldHeight)

    return smoothMin(smoothMin(south, north), smoothMin(west, east))
  }

  /**
   * How far inland this edge has wandered at position [t] along it, in metres. Never negative.
   *
   * Periodic in [t] with period 1, from the *sample path* rather than from the noise: `fields/Noise` has no
   * tileable variant, but any function evaluated around a closed circle is periodic in the angle, and every
   * octave of an fbm closes with it. The circle's radius sets the wavelength - a bigger circle walks further
   * through noise space per turn, so features come out smaller relative to the edge.
   *
   * **This is belt and braces, not the thing that makes the wrap seam safe.** A south shore's wobble would
   * discontinue at `t = 0`, which is `x = 0`, which is several kilometres deep inside the *west* margin - so
   * the south shore does not reach it. What actually hides the seam is that both sides of it are held at the
   * margin floor; see the note in [distanceToEdge] and `the wrap seam is drowned to the same depth on both
   * sides` in `OceanBorderTest`. Closing the loop costs nothing and removes the need to re-derive that
   * argument every time the margin's width changes, which is the only reason it is done.
   */
  private fun wobbleAlong(salt: Long, t: Double): Double {
    if (wobbleMetres <= 0.0) return 0.0

    val angle = t * 2.0 * Math.PI
    val radius = (worldWidth + worldHeight) * 0.5 / (2.0 * Math.PI * WOBBLE_WAVELENGTH)
    val noise = Noise.fbm(
      seed = GenRng.mix64(wobbleSeed xor salt),
      x = cos(angle) * radius,
      y = sin(angle) * radius,
      octaves = WOBBLE_OCTAVES
    )

    // fbm is [-1, 1]; this has to be non-negative or the invariant above is void.
    return wobbleMetres * (0.5 + 0.5 * noise)
  }

  /**
   * Polynomial smooth minimum. Always `<= min(a, b)`, which is what keeps [distanceToEdge] safe.
   *
   * Rounds off the corners. A hard `min` of two edge distances creases along the diagonal, and four creases
   * meeting at a right angle is exactly what makes a corner of the map look like a corner of a map.
   */
  private fun smoothMin(a: Double, b: Double): Double {
    val h = ((CORNER_ROUND - abs(a - b)) / CORNER_ROUND).coerceIn(0.0, 1.0)
    return min(a, b) - h * h * CORNER_ROUND * 0.25
  }

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

    for (i in elevation.data.indices) {
      elevation.data[i] = heightAt(i, elevation.data[i], seaLevel)
    }
  }

  /**
   * What this margin makes of one cell's natural height. The whole of [applyTo], for a single cell.
   *
   * Extracted so that `TectonicsStage.normaliseLandFraction` can ask *what would the finished height of this
   * cell be if the field were shifted by so much* without either duplicating the arithmetic or applying the
   * margin twice. A second copy of it would be a copy that eventually disagrees, and the disagreement would
   * surface as a land fraction that quietly misses its target.
   */
  fun heightAt(i: Int, natural: Double, seaLevel: Double): Double {
    if (!isEnabled) return natural

    val distance = distanceToEdge(worldXOf(i), worldYOf(i))
    val blendEnd = marginMetres * (1.0 + BLEND_SHARE)
    if (distance >= blendEnd) return natural

    val target = seaLevel - depthBelowSeaLevel
    val inwards = PolylineFeature.smoothstep((distance / blendEnd).coerceIn(0.0, 1.0))
    // Towards the target rather than clamped to it: a natural trench deeper than the target is pulled up
    // instead of down, which is harmless - it is still ocean - and keeps this a single continuous blend.
    var height = target + (natural - target) * inwards

    if (distance < marginMetres) {
      val shelf = seaLevel - SHELF_DEPTH
      val toShelf = PolylineFeature.smoothstep((distance / marginMetres).coerceIn(0.0, 1.0))
      height = minOf(height, target + (shelf - target) * toShelf)
    }

    return height
  }

  /**
   * Whether the margin touches this cell at all.
   *
   * The complement of "the margin cannot change this cell's answer", which is what lets the land-fraction
   * search treat most of the world as a fixed histogram and only re-evaluate the band.
   */
  fun isInBlend(i: Int): Boolean {
    if (!isEnabled) return false
    return distanceToEdge(worldXOf(i), worldYOf(i)) < marginMetres * (1.0 + BLEND_SHARE)
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

    /**
     * Along-edge length of one bay in the wobbled coastline, in metres.
     *
     * Absolute rather than a share of the world, and deliberately: this is a coastline, and how long a bay is
     * has nothing to do with how big the continent behind it is. A 128 km world gets about seven bays per
     * edge and a 512 km world twenty-eight of the same size, which is the right way round.
     */
    const val WOBBLE_WAVELENGTH = 18_000.0

    /** Enough octaves for headlands on the bays, not enough for the coast to look eroded by noise. */
    const val WOBBLE_OCTAVES = 3

    /**
     * Metres of corner rounding. The scale over which the smooth minimum blends two edges together.
     *
     * Independent of the wobble: even a perfectly straight margin needs this, because four hard `min` creases
     * meeting at right angles is the other half of what makes the map's edge read as the edge of a map.
     */
    const val CORNER_ROUND = 1_500.0

    // One salt per edge, so opposite shores are not mirror images of each other. They need not agree across
    // a seam - see the note in `distanceToEdge`; both sides of a seam are drowned regardless.
    private const val SOUTH_SALT = 0x501D7L
    private const val NORTH_SALT = 0x0BEEF1L
    private const val WEST_SALT = 0x0DEC0DL
    private const val EAST_SALT = 0x0FACADL

    fun of(
      config: WorldConfig,
      depthBelowSeaLevel: Double,
      region: CellRegion,
      metresPerCell: Double,
      gridWidth: Int,
      wobbleMetres: Double = 0.0
    ) = OceanBorder(
      region = region,
      metresPerCell = metresPerCell,
      gridWidth = gridWidth,
      worldWidth = config.widthMetres,
      worldHeight = config.heightMetres,
      marginMetres = config.oceanBorderMetres,
      depthBelowSeaLevel = depthBelowSeaLevel,
      wobbleMetres = wobbleMetres,
      // From the world's own seed, so both construction sites - tectonics and, after erosion, the second
      // application - produce the *same* coastline. Two different wobbles would carve a step at their
      // difference, which is the failure the second application exists to prevent.
      wobbleSeed = GenRng.mix64(config.seed xor BORDER_SALT)
    )

    private const val BORDER_SALT = 0x0B02DEL
  }
}
