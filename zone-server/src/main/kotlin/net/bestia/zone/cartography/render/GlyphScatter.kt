package net.bestia.zone.cartography.render

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

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
    sizePixels: Double,

    /**
     * Whether open ground carries its own marks as well as woodland.
     *
     * Off leaves grassland, steppe and scree as bare tinted paper, which is what the reference plates do and
     * what keeps the map quiet. On gives most biomes a symbol of their own, so the ground says what it is
     * without the reader having to learn a colour.
     */
    openGround: Boolean = false,

    /**
     * Ground already taken, as `[worldX, worldY, radiusMetres]` triples.
     *
     * How the cover pass is told where the ranges went. The alternative - letting the two scatters run blind
     * and relying on draw order - does not work once symbols are opaque: whichever family is drawn second
     * punches holes in the other, and a wood drawn over a range hides the peaks it is standing on.
     */
    avoid: List<DoubleArray> = emptyList()
  ): List<Glyph> {
    val pitch = spacingPixels * view.metresPerPixel
    val level = levelSaltOf(view.metresPerPixel)
    val salt = GenRng.hash(inputs.seed, family.ordinal.toLong(), level)

    // A glyph is drawn about its position, so one whose centre is off-tile can still put ink on it - and a
    // relief glyph slides across its ridge before it is drawn, so its cell can start further out again.
    val reach = sizePixels * GLYPH_REACH_FACTOR * view.metresPerPixel
    val drift = if (family == GlyphKind.Family.RELIEF) CREST_STEPS * CREST_STEP_FRACTION * pitch else 0.0
    val bounds = view.bounds.expanded(reach + drift)

    // Slack beyond that, because a relief glyph inside the view is kept or dropped according to its
    // neighbours - see [keepsCompany] and [isBuried] - and a neighbour just outside the view has to be seen or
    // two tiles sharing a range disagree about which of its peaks were drawn.
    val margin = if (family == GlyphKind.Family.RELIEF) RELIEF_NEIGHBOUR_CELLS else COVER_NEIGHBOUR_CELLS
    val fromX = floor(bounds.minX / pitch).toLong() - margin
    val toX = ceil(bounds.maxX / pitch).toLong() + margin
    val fromY = floor(bounds.minY / pitch).toLong() - margin
    val toY = ceil(bounds.maxY / pitch).toLong() + margin

    val cols = (toX - fromX + 1).toInt()
    val rows = (toY - fromY + 1).toInt()
    val placed = arrayOfNulls<Placed>(cols * rows)
    val clearance = Clearance.of(inputs, bounds, sizePixels * CLEARANCE_GLYPHS * view.metresPerPixel, avoid)

    for (cellY in fromY..toY) {
      for (cellX in fromX..toX) {
        placed[(cellY - fromY).toInt() * cols + (cellX - fromX).toInt()] =
          place(
            inputs, family, salt, cellX, cellY, pitch, clearance,
            sizePixels * view.metresPerPixel, openGround
          )
      }
    }

    // Built for every candidate including the slack ring, and only then filtered down to the ones on the tile.
    // Both relief filters are decided against this full set, which is what makes them frame-independent: a
    // glyph's fate depends on its neighbours in the world, never on which tile happened to be drawing it.
    val all = ArrayList<Glyph>(cols * rows)
    val onTile = ArrayList<Boolean>(cols * rows)

    for (row in 0 until rows) {
      for (col in 0 until cols) {
        val candidate = placed[row * cols + col] ?: continue
        val company = if (family == GlyphKind.Family.RELIEF) MIN_RELIEF_COMPANY else MIN_COVER_COMPANY
        if (neighbours(placed, cols, rows, col, row) < company) continue

        val cellX = fromX + col
        val cellY = fromY + row
        val jitter = if (family == GlyphKind.Family.COVER) COVER_SIZE_JITTER else RELIEF_SIZE_JITTER
        val scale = (1.0 - jitter) + 2.0 * jitter * GenRng.hashUnit(salt, cellX, cellY, SIZE_SALT)

        all += Glyph(
          kind = candidate.kind,
          x = view.screenX(candidate.worldX),
          y = view.screenY(candidate.worldY),
          worldX = candidate.worldX,
          worldY = candidate.worldY,
          size = sizePixels * scale * kindScale(candidate.kind, candidate.site),
          lean = leanOf(candidate.kind, candidate.site, salt, cellX, cellY),
          variant = GenRng.hash(salt, cellX, cellY, VARIANT_SALT)
        )
        onTile += bounds.contains(candidate.worldX, candidate.worldY)
      }
    }

    val glyphs = ArrayList<Glyph>(all.size)
    for (i in all.indices) {
      if (!onTile[i]) continue
      if (family != GlyphKind.Family.RELIEF) {
        glyphs += all[i]
        continue
      }

      val absorbed = merge(i, all) ?: continue
      glyphs += if (absorbed == 0) all[i] else all[i].copy(size = all[i].size * grownBy(absorbed))
    }

    // Screen y grows south, so ascending y is north-to-south, and a southern glyph is drawn over a northern
    // one. With opaque symbols that ordering *is* the depth cue: a nearer peak hides the shoulder of the one
    // behind it instead of the two being drawn through each other.
    glyphs.sortBy { it.y }
    return glyphs
  }

  /** One lattice cell's decision: where its symbol stands, what it is, and the ground it found. */
  private class Placed(val worldX: Double, val worldY: Double, val kind: GlyphKind, val site: Site)

  private fun place(
    inputs: TileInputs,
    family: GlyphKind.Family,
    salt: Long,
    cellX: Long,
    cellY: Long,
    pitch: Double,
    clearance: Clearance,
    footprint: Double,
    openGround: Boolean
  ): Placed? {
    val jitterX = GenRng.hashUnit(salt, cellX, cellY, JITTER_X_SALT) - 0.5
    val jitterY = GenRng.hashUnit(salt, cellX, cellY, JITTER_Y_SALT) - 0.5

    var worldX = (cellX + 0.5 + jitterX * JITTER) * pitch
    var worldY = (cellY + 0.5 + jitterY * JITTER) * pitch

    if (family == GlyphKind.Family.RELIEF) {
      val crest = snapToCrest(inputs, worldX, worldY, pitch)
      worldX = crest[0]
      worldY = crest[1]
    }

    if (clearance.blocks(worldX, worldY)) return null
    if (!standsOnLand(inputs, worldX, worldY, footprint)) return null

    val site = siteAt(inputs, worldX, worldY) ?: return null
    val kind = when (family) {
      GlyphKind.Family.RELIEF -> reliefKind(site)
      GlyphKind.Family.COVER -> coverKind(site, salt, cellX, cellY, openGround)
    } ?: return null

    return Placed(worldX, worldY, kind, site)
  }

  /**
   * Whether the symbol's whole footprint stands on dry ground.
   *
   * Testing the centre alone is not enough, and the failure is conspicuous: the shelf just off a coast is
   * often steep enough to qualify as relief while sitting a metre or two above the water, so a peak would be
   * drawn on a shoal too small to see and appear to stand in the open sea. Probing the corners of the area
   * the symbol will occupy asks the question the drawing actually poses - is there land here to stand on -
   * rather than the one the point happens to answer.
   */
  private fun standsOnLand(inputs: TileInputs, worldX: Double, worldY: Double, footprint: Double): Boolean {
    val elevation = inputs.elevation
    val reach = footprint * FOOTPRINT_PROBE

    for (step in 0 until FOOTPRINT_PROBES) {
      val angle = step * 2.0 * Math.PI / FOOTPRINT_PROBES
      val ground = elevation.sampleBicubic(worldX + cos(angle) * reach, worldY + sin(angle) * reach)
      if (ground.isNaN() || ground < inputs.seaLevel) return false
    }

    return true
  }

  /**
   * How many of the eight neighbouring cells also produced a symbol.
   *
   * Both families use it and both want a different answer. A lone peak is almost always the placement
   * failing rather than the ground saying anything, because every rise in a real landscape belongs to
   * something longer. A lone *tree* is worse: it is a symbol for a wood standing where there is no wood, and
   * a reader takes it for a landmark. So cover needs two neighbours before it is drawn at all, which is the
   * smallest group that reads as a stand rather than as a mark.
   */
  private fun neighbours(placed: Array<Placed?>, cols: Int, rows: Int, col: Int, row: Int): Int {
    var found = 0

    for (dy in -1..1) {
      for (dx in -1..1) {
        if (dx == 0 && dy == 0) continue

        val x = col + dx
        val y = row + dy
        if (x < 0 || y < 0 || x >= cols || y >= rows) continue
        if (placed[y * cols + x] != null) found++
      }
    }

    return found
  }

  /**
   * Whether a symbol survives crowding, and how many of its neighbours it swallowed: null where it is dropped.
   *
   * Two peaks drawn closer than [MERGE_SPACING] do not read as two summits, they read as one summit with a
   * broken outline, so the smaller is removed and the survivor grows a little for each one it took. A range
   * that would have been a row of clipped triangles becomes a shorter row of larger ones, which is both what
   * the reference plates show and what actually happens as a map is generalised: detail does not thin out
   * evenly, it consolidates.
   *
   * Every decision here is a pairwise predicate over the *whole* candidate set, including the ones that were
   * themselves dropped, so it never depends on the order symbols are visited. That is what lets two tiles
   * sharing a range agree about which of its peaks were drawn and how big each one ended up.
   */
  private fun merge(index: Int, glyphs: List<Glyph>): Int? {
    val glyph = glyphs[index]
    var absorbed = 0

    for (other in glyphs.indices) {
      if (other == index) continue

      val rival = glyphs[other]
      if (!crowds(glyph, rival)) continue
      if (outranks(rival, glyph)) return null

      absorbed++
    }

    return absorbed
  }

  /** Whether two symbols stand too close to be drawn as two things. */
  private fun crowds(a: Glyph, b: Glyph): Boolean {
    val limit = MERGE_SPACING * (a.size + b.size)
    val dx = a.x - b.x
    if (abs(dx) > limit) return false

    val dy = a.y - b.y
    return dx * dx + dy * dy < limit * limit
  }

  /** How much a survivor grows, tailing off so one peak cannot swell to swallow a whole range. */
  private fun grownBy(absorbed: Int): Double =
    (1.0 + MERGE_GROWTH * absorbed).coerceAtMost(MAX_MERGE_GROWTH)

  /**
   * Which of two overlapping symbols is the one worth keeping: the larger, and on a tie the higher variant.
   *
   * The tie-break has to exist and has to be arbitrary. Two symbols of exactly equal footprint would otherwise
   * each be judged buried by the other and both would vanish, which is a hole rather than a peak.
   */
  private fun outranks(a: Glyph, b: Glyph): Boolean {
    val areaA = a.size * a.size * a.kind.aspect
    val areaB = b.size * b.size * b.kind.aspect
    if (areaA != areaB) return areaA > areaB

    return a.variant > b.variant
  }

  /**
   * The ground no symbol may stand on: the lines and places the map is actually about.
   *
   * A wood drawn over a river hides the river, and the river is the thing a reader traces with a finger. The
   * drawing order cannot fix it either way round - glyphs under the water ink leave tree crowns poking through
   * it, and glyphs over it break the line - so the space has to be cleared before anything is placed.
   *
   * A grid of flags rather than a distance test against the geometry, because the geometry is the wrong shape
   * for the question: one river in view is thousands of segments, and testing every candidate against every
   * segment is the product of two large numbers. Stamping each segment once into a coarse grid and then asking
   * the grid costs the sum instead.
   */
  private class Clearance(
    private val cell: Double,
    private val minX: Double,
    private val minY: Double,
    private val cols: Int,
    private val rows: Int,
    private val blocked: BooleanArray
  ) {

    fun blocks(worldX: Double, worldY: Double): Boolean {
      val col = floor((worldX - minX) / cell).toInt()
      val row = floor((worldY - minY) / cell).toInt()
      if (col < 0 || row < 0 || col >= cols || row >= rows) return false

      return blocked[row * cols + col]
    }

    private fun mark(worldX: Double, worldY: Double, radiusMetres: Double) =
      mark(worldX, worldY, ceil(radiusMetres / cell).toInt().coerceAtLeast(1))

    private fun mark(worldX: Double, worldY: Double, radiusCells: Int) {
      val col = floor((worldX - minX) / cell).toInt()
      val row = floor((worldY - minY) / cell).toInt()

      for (dy in -radiusCells..radiusCells) {
        for (dx in -radiusCells..radiusCells) {
          val x = col + dx
          val y = row + dy
          if (x < 0 || y < 0 || x >= cols || y >= rows) continue
          blocked[y * cols + x] = true
        }
      }
    }

    private fun markOutline(feature: VectorFeature) {
      for (line in feature.outline()) {
        val points = line.points
        for (i in 0 until points.size - 1) {
          val from = points[i]
          val to = points[i + 1]
          val steps = (hypot(to.x - from.x, to.y - from.y) / (cell * STAMP_STEP_CELLS)).toInt() + 1

          for (step in 0..steps) {
            val t = step.toDouble() / steps
            mark(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, 1)
          }
        }
      }
    }

    companion object {

      fun of(inputs: TileInputs, bounds: Aabb, margin: Double, avoid: List<DoubleArray>): Clearance {
        val cell = margin.coerceAtLeast(MIN_CLEARANCE_METRES)
        val area = bounds.expanded(cell * PLACE_CLEARANCE_CELLS)
        val cols = ceil(area.width / cell).toInt() + 1
        val rows = ceil(area.height / cell).toInt() + 1

        val grid = Clearance(cell, area.minX, area.minY, cols, rows, BooleanArray(cols * rows))

        for (feature in inputs.featuresIn(area)) {
          when (feature.kind) {
            FeatureKind.RIVER_CHANNEL, FeatureKind.ROAD, FeatureKind.BRIDGE, FeatureKind.SEA_LANE,
            FeatureKind.LAKE, FeatureKind.OXBOW_LAKE -> grid.markOutline(feature)

            // A settlement is a point, and the space it needs is the space its symbol and its name will take,
            // which is a good deal wider than the road running into it.
            FeatureKind.SETTLEMENT -> if (feature is PointMarker) {
              grid.mark(feature.position.x, feature.position.y, PLACE_CLEARANCE_CELLS)
            }

            else -> Unit
          }
        }

        for (taken in avoid) {
          grid.mark(taken[0], taken[1], taken[2])
        }

        return grid
      }
    }
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
    val curvature = ridgeAt(inputs, worldX, worldY)?.get(0) ?: 0.0

    return Site(ground, dzdx, dzdy, hypot(dzdx, dzdy), curvature, biome, canopy, ice)
  }

  /**
   * Whether a candidate stands on a crest worth drawing, and how big a landform it is.
   *
   * Slope is the wrong test and was the reason the first attempts came out as confetti: **both flanks of every
   * valley are steep**, so a slope threshold scatters peaks down hillsides and across the valley floor between
   * them, and no amount of thinning turns a scatter into a range. Cross-ridge curvature is the right test - it
   * is near zero on a flank however steep, negative on a crest and positive in a trough - so it selects the
   * lines a range is actually made of.
   */
  private fun reliefKind(site: Site): GlyphKind? = when {
    site.biome == Biome.OCEAN || site.biome == Biome.LAKE -> null
    // A crest, or ground too steep to walk. The second arm exists because this scatter is now the *only*
    // thing that says anything about the shape of the land: with the hillshade gone, a steep face carrying no
    // symbol is indistinguishable from a meadow, so steepness earns a mark whether or not it is a ridge.
    site.curvature > RIDGE_CURVATURE && site.slope < STEEP_SLOPE -> null
    site.ground < RELIEF_MIN_ELEVATION && site.slope < HILL_SLOPE -> null
    isMountainous(site) -> GlyphKind.MOUNTAIN
    else -> GlyphKind.HILL
  }

  /**
   * Whether the ground is mountain rather than hill: steep enough to be hard going, high, or classified alpine.
   *
   * The biome arm matters as much as the two measurements. A plateau above the treeline is mountain country to
   * anyone crossing it whatever its local gradient says, and the classifier already knows that.
   */
  private fun isMountainous(site: Site): Boolean =
    site.slope >= MOUNTAIN_SLOPE || site.ground >= MOUNTAIN_ELEVATION || site.biome == Biome.ALPINE

  /**
   * Whether the ground is bare mountainside, where a range is drawn and no wood is.
   *
   * A much higher bar than [isMountainous], and the two must not be confused - they were once, and it cleared
   * the trees off most of a temperate world. [isMountainous] answers "is this symbol a peak or a rounded
   * rise", which wants a low threshold because a drawn map gives a peak to any real ridge; this answers "does
   * anything grow here", which is a question about the treeline and wants a high one.
   */
  private fun isAboveTreeline(site: Site): Boolean =
    site.slope >= TREELINE_SLOPE || site.ground >= TREELINE_ELEVATION ||
        site.biome == Biome.ALPINE || site.biome == Biome.ICE_SHEET

  /**
   * Slides a candidate sideways onto the crest line above it, without letting it run along that line.
   *
   * This is what turns a lattice into a ridge. Plain gradient ascent does not: at a crest the fall across the
   * ridge is already zero while the fall *along* it is not, so an ascent keeps climbing towards the summit and
   * every candidate for miles around ends up stacked on one peak. Moving only along the direction of greatest
   * curvature - across the ridge, never along it - converges onto the crest and stops there, so the candidates
   * keep the spacing they had *down* the range and lose the spacing they had *across* it.
   *
   * That is exactly the reference plate's geometry: peaks strung along a spine at even intervals, bare ground
   * on both sides, and no two symbols piled on the same summit.
   */
  private fun snapToCrest(inputs: TileInputs, startX: Double, startY: Double, pitch: Double): DoubleArray {
    val at = doubleArrayOf(startX, startY)
    val step = pitch * CREST_STEP_FRACTION

    repeat(CREST_STEPS) {
      val ridge = ridgeAt(inputs, at[0], at[1]) ?: return at
      val slope = gradientAt(inputs, at[0], at[1]) ?: return at

      val across = slope[0] * ridge[1] + slope[1] * ridge[2]
      if (abs(across) < CREST_MIN_SLOPE) return at

      val direction = if (across > 0.0) 1.0 else -1.0
      at[0] += ridge[1] * direction * step
      at[1] += ridge[2] * direction * step
    }

    return at
  }

  /**
   * The smaller principal curvature and the unit direction it acts in, as `[curvature, nx, ny]`.
   *
   * The Hessian of the surface, diagonalised. Its smaller eigenvalue is how sharply the ground turns over in
   * the direction it bends most, and the matching eigenvector is that direction - across a ridge rather than
   * along it. Sampled at [CURVATURE_STEP_CELLS] of the raster's own cell, so the ridges found are the ones the
   * elevation layer actually holds and do not move as the map is zoomed.
   */
  private fun ridgeAt(inputs: TileInputs, worldX: Double, worldY: Double): DoubleArray? {
    val elevation = inputs.elevation
    val h = elevation.region.resolution.metresPerCell * CURVATURE_STEP_CELLS

    val centre = elevation.sampleBicubic(worldX, worldY)
    val east = elevation.sampleBicubic(worldX + h, worldY)
    val west = elevation.sampleBicubic(worldX - h, worldY)
    val north = elevation.sampleBicubic(worldX, worldY + h)
    val south = elevation.sampleBicubic(worldX, worldY - h)
    val northEast = elevation.sampleBicubic(worldX + h, worldY + h)
    val northWest = elevation.sampleBicubic(worldX - h, worldY + h)
    val southEast = elevation.sampleBicubic(worldX + h, worldY - h)
    val southWest = elevation.sampleBicubic(worldX - h, worldY - h)

    if (centre.isNaN() || east.isNaN() || west.isNaN() || north.isNaN() || south.isNaN() ||
      northEast.isNaN() || northWest.isNaN() || southEast.isNaN() || southWest.isNaN()
    ) {
      return null
    }

    val zxx = (east - 2.0 * centre + west) / (h * h)
    val zyy = (north - 2.0 * centre + south) / (h * h)
    val zxy = (northEast - southEast - northWest + southWest) / (4.0 * h * h)

    val mean = (zxx + zyy) / 2.0
    val half = (zxx - zyy) / 2.0
    val radius = sqrt(half * half + zxy * zxy)
    val curvature = mean - radius

    var nx = zxy
    var ny = curvature - zxx
    val length = hypot(nx, ny)
    if (length < EIGENVECTOR_EPSILON) {
      // Curvature is isotropic here - a dome or a bowl - so any direction is as good as another.
      nx = 1.0
      ny = 0.0
    } else {
      nx /= length
      ny /= length
    }

    return doubleArrayOf(curvature, nx, ny)
  }

  /** The uphill gradient in metres per metre, or null where the field has nothing to say. */
  private fun gradientAt(inputs: TileInputs, worldX: Double, worldY: Double): DoubleArray? {
    val region = inputs.elevation.region
    val step = region.resolution.metresPerCell * SLOPE_STEP_CELLS

    val east = inputs.elevation.sampleBicubic(worldX + step, worldY)
    val west = inputs.elevation.sampleBicubic(worldX - step, worldY)
    val north = inputs.elevation.sampleBicubic(worldX, worldY + step)
    val south = inputs.elevation.sampleBicubic(worldX, worldY - step)
    if (east.isNaN() || west.isNaN() || north.isNaN() || south.isNaN()) return null

    return doubleArrayOf((east - west) / (2.0 * step), (north - south) / (2.0 * step))
  }

  /**
   * Cover is thinned by its own density rather than cut off at a threshold.
   *
   * A canopy cut-off draws a hard edge around every wood at exactly the contour where cover crosses it, which
   * is the one thing a scattered symbol is supposed to avoid. Comparing a per-cell hash against the cover
   * share instead makes the wood thin out towards its margin, so the edge is ragged and reads as a treeline.
   */
  private fun coverKind(site: Site, salt: Long, cellX: Long, cellY: Long, openGround: Boolean): GlyphKind? {
    if (site.biome == null || site.biome == Biome.OCEAN || site.biome == Biome.LAKE) return null

    if (site.ice >= ICE_GLYPH_METRES) return GlyphKind.ICE

    when (site.biome) {
      Biome.BOG, Biome.SWAMP -> return GlyphKind.MARSH
      Biome.DESERT -> return if (site.slope < DUNE_MAX_SLOPE) GlyphKind.DUNE else null
      else -> Unit
    }

    // Mountain ground carries its own symbol and nothing else. A crown drawn over a range fills the gaps
    // between its peaks and the range stops reading as one landform - and a map has to choose which of the two
    // it is saying about a mountainside, because it cannot say both at this scale.
    if (isAboveTreeline(site)) return null

    // One roll decides both, so a cell is a tree or open ground and never both. The wood takes the bottom of
    // the range and what is left over is offered to the open-ground mark, which is why a closing canopy
    // squeezes the tussocks out rather than being drawn on top of them.
    val roll = GenRng.hashUnit(salt, cellX, cellY, COVER_SALT)
    val wood = canopyShare(site.canopy)

    if (roll <= wood) {
      return when (site.biome) {
        Biome.TAIGA, Biome.ALPINE, Biome.TUNDRA -> GlyphKind.CONIFER
        // A palm is a coastal symbol, not a tropical one. Giving it every tropical forest covered a third of
        // a warm world in one repeated umbrella, which reads as a printed pattern rather than as woodland -
        // and a seasonal tropical forest is closed broadleaf canopy anyway, which a crown draws.
        Biome.BEACH -> GlyphKind.PALM
        else -> GlyphKind.BROADLEAF
      }
    }

    if (!openGround || roll > wood + OPEN_GROUND_SHARE) return null

    return openKind(site.biome)
  }

  /** The mark for ground with no wood on it, or null where the biome is better left bare. */
  private fun openKind(biome: Biome?): GlyphKind? = when (biome) {
    Biome.GRASSLAND, Biome.TROPICAL_SEASONAL_FOREST -> GlyphKind.GRASS
    Biome.DRYLAND -> GlyphKind.SCRUB
    Biome.BADLANDS, Biome.VOLCANIC_FIELD, Biome.GEOTHERMAL_BASIN, Biome.ALPINE -> GlyphKind.ROCK
    Biome.TUNDRA, Biome.COLD_DESERT -> GlyphKind.HUMMOCK
    // Temperate and tropical forest floor, taiga, beach, wetland, desert and ice all have a mark already or
    // read better as bare paper. Left null rather than given a filler.
    else -> null
  }

  /**
   * How much of a lattice cell a wood fills, from the cover underneath it.
   *
   * A floor rather than a straight proportion. Scaling density with cover all the way down draws the thin tail
   * of the distribution as *single trees standing in open country*, which the eye reads as a symbol for
   * something rather than as the edge of a wood. Below [CANOPY_FLOOR] nothing is drawn at all, and the ramp
   * from there to [CANOPY_CLOSED] is short, so a wood has a findable edge and is densely drawn inside it.
   *
   * A deliberate reversal of the reasoning that stood here before, which was that a cut-off draws a hard
   * contour around every wood. It does, and a drawn map wants one: the reference plates outline their forests
   * rather than fading them out.
   */
  private fun canopyShare(canopy: Double): Double {
    if (canopy < CANOPY_FLOOR) return 0.0

    return ((canopy - CANOPY_FLOOR) / (CANOPY_CLOSED - CANOPY_FLOOR)).coerceAtMost(1.0)
  }

  /**
   * How big a symbol stands, which on a drawn map is the only thing that says how big the landform is.
   *
   * Height carries most of it and the sharpness of the crest carries the rest, so the spine of a range draws
   * larger than the shoulders running off it and a high range draws larger than a low one. The growth is wide
   * on purpose - a peak at the top of the scale is over twice the one at the bottom - because a range whose
   * symbols are all one size is a row of stamps however well it is placed.
   */
  private fun kindScale(kind: GlyphKind, site: Site): Double = when (kind) {
    GlyphKind.MOUNTAIN -> {
      val height = (site.ground / PEAK_FULL_HEIGHT).coerceIn(0.0, 1.0)
      val sharpness = (site.curvature / PEAK_FULL_CURVATURE).coerceIn(0.0, 1.0)
      1.0 + PEAK_GROWTH * (HEIGHT_SHARE * height + (1.0 - HEIGHT_SHARE) * sharpness)
    }

    GlyphKind.HILL -> HILL_SCALE * (1.0 + HILL_GROWTH * (site.ground / PEAK_FULL_HEIGHT).coerceIn(0.0, 1.0))
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
    /** Cross-ridge curvature: negative on a crest, near zero on a flank, positive in a trough. */
    val curvature: Double,
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

  /**
   * Where a crest is drawn as a peak rather than as a rounded rise.
   *
   * Low compared with any physical definition of a mountain, and deliberately: on a drawn map the peak is the
   * symbol for *a range*, and the reference plates give one to every ridge worth naming. Reserving it for
   * ground above 1900 m left almost every range on a temperate world drawn as a row of small arcs, which reads
   * as scrub.
   */
  private const val MOUNTAIN_SLOPE = 0.07
  private const val MOUNTAIN_ELEVATION = 900.0

  /** Ground too steep to walk, which earns a symbol whether or not it stands on a crest. */
  private const val STEEP_SLOPE = 0.13

  /** Where the wood stops and bare rock begins - see [isAboveTreeline], which is not [isMountainous]. */
  private const val TREELINE_SLOPE = 0.15
  private const val TREELINE_ELEVATION = 1500.0
  private const val HILL_SLOPE = 0.042
  private const val HILL_ELEVATION = 1250.0

  /**
   * How negative the cross-ridge curvature must be before a crest is drawn at all, and where a peak reaches
   * full size. In metres of fall per metre squared, over a [CURVATURE_STEP_CELLS] sample.
   *
   * The first number is what clears the valleys and the flanks; the second is what makes a sharp spine draw
   * larger than a broad swell. Both are small because the elevation raster is kilometre-scale: a ridge standing
   * 300 m over half a kilometre is about -0.002 here.
   */
  private const val RIDGE_CURVATURE = -0.00009
  private const val PEAK_FULL_CURVATURE = -0.0026

  /** Below this, a crest has to earn its symbol on slope instead - a sea cliff is a ridge, a beach berm is not. */
  private const val RELIEF_MIN_ELEVATION = 150.0

  /** How far apart the curvature samples sit, as a share of a raster cell. */
  private const val CURVATURE_STEP_CELLS = 0.5

  /** Below this length the Hessian is isotropic and its eigenvector is arbitrary rather than wrong. */
  private const val EIGENVECTOR_EPSILON = 1e-12

  /**
   * How far a candidate may slide across the ridge, as a share of the lattice pitch, and in how many steps.
   *
   * Generous compared with an ascent, because this motion is *across* the range only: a candidate has to be
   * able to reach the crest from anywhere on the flank it started on, and unlike a plain ascent it cannot run
   * away along the spine once it arrives.
   */
  private const val CREST_STEPS = 8
  private const val CREST_STEP_FRACTION = 0.28
  private const val CREST_MIN_SLOPE = 0.004

  /** A hill is drawn smaller than a peak at the same lattice pitch. */
  private const val HILL_SCALE = 0.62

  private const val PEAK_GROWTH = 1.35
  private const val PEAK_FULL_HEIGHT = 2200.0

  /** How much of a peak's size comes from its height rather than from how sharp its crest is. */
  private const val HEIGHT_SHARE = 0.65

  /** A hill grows with height too, but far less: it is the symbol for ground that is not a mountain. */
  private const val HILL_GROWTH = 0.4

  /**
   * Multiplies canopy cover before it is compared against the thinning roll.
   *
   * Above one because cover rarely approaches its own maximum: the densest rainforest cell in a world sits
   * near 0.9 and most woodland is 0.4 to 0.6, so an unscaled comparison draws a wood at half the density the
   * data describes and temperate country comes out looking cleared.
   */
  /** Cover below which no tree is drawn, and the cover at which a wood is drawn solid. */
  private const val CANOPY_FLOOR = 0.24
  private const val CANOPY_CLOSED = 0.30

  /** Share of the cells a wood does not take that open ground marks, where the biome has one. */
  private const val OPEN_GROUND_SHARE = 0.42

  private const val ICE_GLYPH_METRES = 25.0
  private const val DUNE_MAX_SLOPE = 0.02

  private const val COVER_LEAN = 0.13
  private const val RELIEF_LEAN = 0.30

  /** Radians of lean per unit of east-west gradient. A 1-in-4 slope tips a peak about ten degrees. */
  private const val LEAN_PER_SLOPE = 0.75

  /**
   * How far a symbol's size may wander from its family's, either way.
   *
   * Wide for relief, because a range wants peaks of visibly different sizes, and narrow for cover, because a
   * wood is many of one thing: crowns of obviously different sizes side by side read as several kinds of tree
   * rather than as canopy.
   */
  private const val RELIEF_SIZE_JITTER = 0.24
  private const val COVER_SIZE_JITTER = 0.09

  /**
   * How close two symbols may stand, as a share of their combined half-widths, before they merge into one.
   *
   * Under one, so some overlap survives: peaks standing partly in front of each other is the depth cue a
   * drawn range is made of, and spacing them until they never touch gives a row of separate triangles.
   */
  private const val MERGE_SPACING = 0.78

  /** How much a survivor grows per symbol it absorbed, and the ceiling on that growth. */
  private const val MERGE_GROWTH = 0.16
  private const val MAX_MERGE_GROWTH = 1.7

  /**
   * How many cells of candidates are computed beyond the drawn area, for the two relief filters.
   *
   * Two rather than one: a peak grown to full size is wider than the lattice pitch, so a symbol two cells away
   * can still bury it, and a ring one cell deep would let a tile edge change the answer.
   */
  private const val RELIEF_NEIGHBOUR_CELLS = 2

  /** One ring of slack is all the cover filter needs, since it only ever looks at its eight neighbours. */
  private const val COVER_NEIGHBOUR_CELLS = 1

  /** Neighbours a symbol needs before it is drawn: one for a range, two for a stand of trees. */
  private const val MIN_RELIEF_COMPANY = 1
  private const val MIN_COVER_COMPANY = 2

  /** How far out the land probes sit, as a share of the symbol's half-width, and how many there are. */
  private const val FOOTPRINT_PROBE = 0.9
  private const val FOOTPRINT_PROBES = 6

  /** How much clear ground a symbol leaves around a river, road or town, in multiples of its own half-width. */
  private const val CLEARANCE_GLYPHS = 1.3

  /** A clearance cell never smaller than this, so a world-zoom grid stays a sane size in memory. */
  private const val MIN_CLEARANCE_METRES = 60.0

  /** How many cells of clear ground a settlement takes, for its own symbol and its name. */
  private const val PLACE_CLEARANCE_CELLS = 3

  /** How far apart a segment is stamped into the clearance grid, in cells. Under one, or it leaves gaps. */
  private const val STAMP_STEP_CELLS = 0.5

  private const val JITTER_X_SALT = 1L
  private const val JITTER_Y_SALT = 2L
  private const val SIZE_SALT = 3L
  private const val LEAN_SALT = 4L
  private const val COVER_SALT = 5L
  private const val VARIANT_SALT = 6L
}
