package net.bestia.zone.cartography.render

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.render.Viewport
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Where the symbols go. A jittered lattice in world space, sampled per tile.
 *
 * ### Why a lattice and not Poisson disk
 *
 * `fields/PoissonDisk` gives more even spacing and is the obvious tool, but it is the wrong shape for a tiled
 * map. Bridson's algorithm grows from a seed point through an active front, so its output depends on the
 * *region* it was run over: to keep two tiles agreeing about a wood they share, the sampling has to be run
 * over blocks on a fixed lattice and gathered - which reintroduces the seam it was meant to avoid, because
 * points in neighbouring blocks know nothing about each other and clump where the blocks meet.
 *
 * A jittered lattice has no region at all. Cell `(i, j)` holds one candidate at a position hashed from `i` and
 * `j`, so a tile can compute exactly the cells it overlaps and get bit-identical answers to every other tile
 * that overlaps them. Spacing is less even than Poisson's, which for hand-drawn symbols is a gain rather than
 * a cost - a perfectly even forest looks printed.
 *
 * ### Spacing is per zoom, and that is deliberate
 *
 * The lattice pitch is a number of *pixels*, so a mountain glyph stands about the same size and the same
 * distance from its neighbours at every zoom, and the map stays equally dense as you zoom. The consequence is
 * that the glyphs at one level are not a subset of those at another: zooming in does not reveal more trees in
 * the same places, it draws the wood again at a finer grain. That is what map generalisation is, and the
 * alternative - a fixed world-space lattice - is either unreadably dense at world zoom or empty at close zoom.
 *
 * What must hold, and does, is that within one level every tile agrees. The level is folded into the seed, so
 * the two lattices are independent rather than accidentally aligned.
 */
object GlyphScatter {

  /**
   * Every glyph of one family whose symbol could touch the viewport.
   *
   * Returned north first, so a caller drawing in order gets southern glyphs overlapping northern ones - the
   * depth cue that makes a drawn range read as a range rather than as a row of triangles.
   */
  fun scatter(
    view: Viewport,
    inputs: TileInputs,
    family: GlyphKind.Family,
    spacingPixels: Double,
    sizePixels: Double
  ): List<Glyph> {
    val pitch = spacingPixels * view.metresPerPixel
    val level = levelSaltOf(view.metresPerPixel)
    val salt = GenRng.hash(inputs.seed, family.ordinal.toLong(), level)

    // A glyph is drawn about its position, so one whose centre is off-tile can still put ink on it.
    val reach = sizePixels * GLYPH_REACH_FACTOR * view.metresPerPixel
    val bounds = view.bounds.expanded(reach)

    val fromX = floor(bounds.minX / pitch).toLong()
    val toX = ceil(bounds.maxX / pitch).toLong()
    val fromY = floor(bounds.minY / pitch).toLong()
    val toY = ceil(bounds.maxY / pitch).toLong()

    val glyphs = ArrayList<Glyph>()

    for (cellY in fromY..toY) {
      for (cellX in fromX..toX) {
        val jitterX = GenRng.hashUnit(salt, cellX, cellY, JITTER_X_SALT) - 0.5
        val jitterY = GenRng.hashUnit(salt, cellX, cellY, JITTER_Y_SALT) - 0.5

        val worldX = (cellX + 0.5 + jitterX * JITTER) * pitch
        val worldY = (cellY + 0.5 + jitterY * JITTER) * pitch

        val site = siteAt(inputs, worldX, worldY) ?: continue
        val kind = when (family) {
          GlyphKind.Family.RELIEF -> reliefKind(site)
          GlyphKind.Family.COVER -> coverKind(site, salt, cellX, cellY)
        } ?: continue

        val variant = GenRng.hash(salt, cellX, cellY, VARIANT_SALT)
        val scale = SIZE_JITTER_MIN +
            (SIZE_JITTER_MAX - SIZE_JITTER_MIN) * GenRng.hashUnit(salt, cellX, cellY, SIZE_SALT)

        glyphs += Glyph(
          kind = kind,
          x = view.screenX(worldX),
          y = view.screenY(worldY),
          size = sizePixels * scale * kindScale(kind, site),
          lean = leanOf(kind, site, salt, cellX, cellY),
          variant = variant
        )
      }
    }

    // Screen y grows south, so ascending y is north-to-south.
    glyphs.sortBy { it.y }
    return glyphs
  }

  /**
   * What the world is like at a candidate position, or null where nothing may be drawn.
   *
   * Sampled straight from the layers rather than through [TerrainRaster], because a glyph may sit outside the
   * tile - and therefore outside even the halo - while still reaching into it. Placement is a world-space
   * question, so answering it in world space costs a few interpolations per glyph and removes the coupling.
   */
  private fun siteAt(inputs: TileInputs, worldX: Double, worldY: Double): Site? {
    val region = inputs.elevation.region
    val metresPerCell = region.resolution.metresPerCell
    if (!region.contains(floor(worldX / metresPerCell).toInt(), floor(worldY / metresPerCell).toInt())) {
      return null
    }

    val ground = inputs.elevation.sampleBicubic(worldX, worldY)
    if (ground.isNaN() || ground < inputs.seaLevel) return null

    // Over a quarter of a cell rather than a whole one: the gradient a range's shape follows is the local
    // one, and a full-cell difference smooths a scarp into the plain beside it.
    val step = metresPerCell * SLOPE_STEP_CELLS
    val east = inputs.elevation.sampleBicubic(worldX + step, worldY)
    val west = inputs.elevation.sampleBicubic(worldX - step, worldY)
    val north = inputs.elevation.sampleBicubic(worldX, worldY + step)
    val south = inputs.elevation.sampleBicubic(worldX, worldY - step)

    val dzdx = (east - west) / (2.0 * step)
    val dzdy = (north - south) / (2.0 * step)

    val biome = Biome.entries.getOrNull(
      inputs.biome[floor(worldX / metresPerCell).toInt(), floor(worldY / metresPerCell).toInt()]
    )

    val canopy = inputs.canopyCover.sampleBilinear(worldX, worldY).let { if (it.isNaN()) 0.0 else it }
    val ice = inputs.iceThickness.sampleBilinear(worldX, worldY).let { if (it.isNaN()) 0.0 else it }

    return Site(ground, dzdx, dzdy, hypot(dzdx, dzdy), biome, canopy, ice)
  }

  private fun reliefKind(site: Site): GlyphKind? = when {
    site.biome == Biome.OCEAN || site.biome == Biome.LAKE -> null
    site.slope >= MOUNTAIN_SLOPE || site.ground >= MOUNTAIN_ELEVATION -> GlyphKind.MOUNTAIN
    site.slope >= HILL_SLOPE || site.ground >= HILL_ELEVATION -> GlyphKind.HILL
    else -> null
  }

  /**
   * Cover is thinned by its own density rather than cut off at a threshold.
   *
   * A canopy cut-off draws a hard edge around every wood at exactly the contour where cover crosses it, which
   * is the one thing a scattered symbol is supposed to avoid. Comparing a per-cell hash against the cover
   * share instead makes the wood thin out towards its margin, so the edge is ragged and reads as a treeline.
   */
  private fun coverKind(site: Site, salt: Long, cellX: Long, cellY: Long): GlyphKind? {
    if (site.biome == null || site.biome == Biome.OCEAN || site.biome == Biome.LAKE) return null

    if (site.ice >= ICE_GLYPH_METRES) return GlyphKind.ICE

    when (site.biome) {
      Biome.BOG, Biome.SWAMP -> return GlyphKind.MARSH
      Biome.DESERT -> return if (site.slope < DUNE_MAX_SLOPE) GlyphKind.DUNE else null
      else -> Unit
    }

    val roll = GenRng.hashUnit(salt, cellX, cellY, COVER_SALT)
    if (roll > site.canopy * CANOPY_GAIN) return null

    return when (site.biome) {
      Biome.TAIGA, Biome.ALPINE, Biome.TUNDRA -> GlyphKind.CONIFER
      Biome.TROPICAL_RAINFOREST, Biome.TROPICAL_SEASONAL_FOREST -> GlyphKind.PALM
      else -> GlyphKind.BROADLEAF
    }
  }

  /** Mountains grow with height; everything else keeps the family size. */
  private fun kindScale(kind: GlyphKind, site: Site): Double = when (kind) {
    GlyphKind.MOUNTAIN -> 1.0 + PEAK_GROWTH * (site.ground / PEAK_FULL_HEIGHT).coerceIn(0.0, 1.0)
    GlyphKind.HILL -> HILL_SCALE
    else -> 1.0
  }

  /**
   * A peak tips away from the fall of the ground, by an amount proportional to how steep it is.
   *
   * Proportional, and not the slope's own direction. The first version took `atan2` of the gradient and
   * clamped it, which looks reasonable and is not: that angle is spread over the whole circle, so clamping it
   * to a small lean saturates almost every glyph at one limit or the other, and a range came out as carets
   * tipped alternately hard left and hard right. Only the east-west component can drive a lean about the
   * page's own axis, and scaling it means a gentle rise tips slightly where a scarp tips hard.
   */
  private fun leanOf(kind: GlyphKind, site: Site, salt: Long, cellX: Long, cellY: Long): Double {
    if (kind.family == GlyphKind.Family.COVER) {
      return (GenRng.hashUnit(salt, cellX, cellY, LEAN_SALT) - 0.5) * 2.0 * COVER_LEAN
    }

    return (site.dzdx * LEAN_PER_SLOPE).coerceIn(-RELIEF_LEAN, RELIEF_LEAN)
  }

  private class Site(
    val ground: Double,
    val dzdx: Double,
    val dzdy: Double,
    val slope: Double,
    val biome: Biome?,
    val canopy: Double,
    val ice: Double
  )

  /**
   * Which zoom this is, folded into the seed so two levels' lattices are independent.
   *
   * Derived from the scale rather than passed in, so a caller cannot forget it and accidentally share a
   * lattice between levels - which would put a wood at the same world position at two zooms and look like it
   * had been placed on purpose, right up until a third level disagreed.
   */
  private fun levelSaltOf(metresPerPixel: Double): Long =
    Math.round(Math.log(metresPerPixel) / Math.log(2.0) * LEVEL_SALT_PRECISION)

  private const val LEVEL_SALT_PRECISION = 16.0

  /** How far a glyph may reach beyond its own half-width, as a multiple of it. */
  private const val GLYPH_REACH_FACTOR = 3.0

  /** Share of a lattice cell a candidate may wander from its centre. Under one, so cells cannot swap order. */
  private const val JITTER = 0.8

  private const val SLOPE_STEP_CELLS = 0.25

  private const val MOUNTAIN_SLOPE = 0.105
  private const val MOUNTAIN_ELEVATION = 1900.0
  private const val HILL_SLOPE = 0.042
  private const val HILL_ELEVATION = 1250.0

  /** A hill is drawn smaller than a peak at the same lattice pitch. */
  private const val HILL_SCALE = 0.62

  private const val PEAK_GROWTH = 0.55
  private const val PEAK_FULL_HEIGHT = 2400.0

  /**
   * Multiplies canopy cover before it is compared against the thinning roll.
   *
   * Above one because cover rarely approaches its own maximum: the densest rainforest cell in a world sits
   * near 0.9 and most woodland is 0.4 to 0.6, so an unscaled comparison draws a wood at half the density the
   * data describes and temperate country comes out looking cleared.
   */
  private const val CANOPY_GAIN = 0.85

  private const val ICE_GLYPH_METRES = 25.0
  private const val DUNE_MAX_SLOPE = 0.02

  private const val COVER_LEAN = 0.13
  private const val RELIEF_LEAN = 0.30

  /** Radians of lean per unit of east-west gradient. A 1-in-4 slope tips a peak about ten degrees. */
  private const val LEAN_PER_SLOPE = 0.75

  private const val SIZE_JITTER_MIN = 0.78
  private const val SIZE_JITTER_MAX = 1.26

  private const val JITTER_X_SALT = 1L
  private const val JITTER_Y_SALT = 2L
  private const val SIZE_SALT = 3L
  private const val LEAN_SALT = 4L
  private const val COVER_SALT = 5L
  private const val VARIANT_SALT = 6L
}
