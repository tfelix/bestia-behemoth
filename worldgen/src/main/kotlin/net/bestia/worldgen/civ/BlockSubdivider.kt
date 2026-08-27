package net.bestia.worldgen.civ

import net.bestia.worldgen.vector.ConvexPolygons
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Cuts a patch into building plots.
 *
 * ### The one thing this exists to produce
 *
 * Plots laid along a street by arc length - which is what [LotPlanner] does, and what every settlement did before
 * this - give a row of houses that follows the street's curve. Every row curves the same way, because every row
 * came from a street, and a town of them reads as a set of terraces rather than as a place. What is missing is the
 * *block*: a group of buildings that share an axis, next to another group that shares a different one.
 *
 * A block is what a patch becomes here, by repeatedly cutting it across its own longest edge until the pieces are
 * plot-sized. Cutting the longest edge is the whole trick: each cut runs the narrow way, so the pieces come out as
 * a row sharing an axis rather than as an ever-finer pinwheel, and the axis is the patch's own - so one patch's
 * houses face one way and its neighbour's face another. That difference, repeated across forty patches, is most of
 * what makes a generated town look built.
 *
 * ### Why a river cannot empty this
 *
 * The failure recorded in [Lot]'s KDoc - one channel taking a city from 574 plots to 68 - happened because a plot
 * existed only if some cycle of streets closed around it. Here a plot exists because a patch was cut, and a patch
 * exists because a Voronoi site had a cell. Neither depends on a cycle. A river costs the plots it physically
 * crosses, one at a time, through the same `frame.buildable` test every plot has always passed.
 */
internal object BlockSubdivider {

  /**
   * Plots cut from one patch.
   *
   * @param streetWidthFor half-width of the street on each edge of the patch, by edge index. The block is set
   *   back from each edge by its own street's width, which is what makes a block fronting an artery sit further
   *   back than one fronting an alley - an asymmetry that reads clearly from above.
   * @param rankFor rank of the street on each edge, so a plot inherits the rank of the street it fronts and
   *   `Zoning` can still put the shops on the high street.
   * @param distanceAt normalised walking distance from the market. See [StreetDistance].
   */
  fun of(
    patch: TownPatch,
    grain: QuarterGrain,
    frame: TownFrame,
    streetWidthFor: (edge: Int) -> Double,
    rankFor: (edge: Int) -> Int,
    distanceAt: (Vec2d) -> Double,
    lotStep: Double,
    /** Largest plot a cut leaf may become, as half-extents. Anything beyond it is garden. */
    maxHalfFrontage: Double,
    maxHalfDepth: Double,
    /**
     * Smallest plot a cut leaf may become, as half-extents. Below either one the leaf is dropped.
     *
     * A floor on the *plot*, because a floor on the building is not expressible: a building may not grow past
     * its plot without landing on its neighbour, so the only way to guarantee a minimum building is to refuse
     * the ground that could not hold one. What is refused becomes yard, which is what a leaf too small to
     * build on always was.
     */
    minHalfFrontage: Double,
    minHalfDepth: Double,
    /**
     * Every plot the settlement has already laid, including the ones from other patches.
     *
     * Shared across the whole town rather than built per patch: a leaf's plot is its *bounding* rectangle and
     * can reach past the leaf, so neighbouring plots - and, where blocks come close, neighbouring patches'
     * plots - have to be tested against each other. See [LotIndex].
     */
    placed: LotIndex,
    salt: Long,
    roll: (Long, Long) -> Double
  ): List<Lot> {
    val block = ConvexPolygons.clean(
      ConvexPolygons.inset(patch.polygon) { edge -> streetWidthFor(edge) }
    )
    if (block.size < 3 || ConvexPolygons.area(block) < grain.minLotArea) return emptyList()

    val leaves = ArrayList<List<Vec2d>>()
    cut(block, grain, salt, 0, roll, leaves)

    val out = ArrayList<Lot>(leaves.size)
    for ((index, leaf) in leaves.withIndex()) {
      val key = salt * 31 + index

      // Dropped *after* the cut rather than by not cutting, so a yard is a plot-shaped hole in a row of houses
      // instead of an uncut quarter of the block. The difference is visible: real gaps in a terrace are one
      // building wide.
      if (roll(key, EMPTY_SALT) < grain.emptyProb) continue

      val lot = lotOf(
        patch, leaf, rankFor, distanceAt, lotStep,
        maxHalfFrontage, maxHalfDepth, minHalfFrontage, minHalfDepth
      ) ?: continue

      // Inside the town, as well as buildable. A Voronoi cell is bounded by its neighbours and by the guard ring,
      // **not** by the town's edge - so an outer cell can reach past it wherever the guards happen to be sparse,
      // and a block cut from that cell puts plots on ground the settlement never graded. Found by the 320-cell
      // sweep as `buildings belong to their settlement`: a building 695 m out on a town whose footprint is 610.
      // Guarded here rather than by clipping the cell, because the boundary is a warped ring and therefore not
      // convex - clipping a cell against all of its edges would intersect down to the ring's kernel and shrink
      // every patch in the middle of the town to pay for one at its edge.
      if (!frame.encloses(lot.centre)) continue
      if (!frame.buildable(lot.centre)) continue
      if (placed.overlaps(lot)) continue

      placed.add(lot)
      out.add(lot)
    }

    return out
  }

  /**
   * Recursive bisection down to plot size.
   *
   * Depth-limited as well as area-limited, because the area test alone is not a termination proof: a cut that
   * fails - a gap wider than the piece, a sliver with no usable halves - returns the piece whole, and a piece that
   * cannot be cut but is still over the minimum would recurse forever on the same geometry.
   */
  private fun cut(
    polygon: List<Vec2d>,
    grain: QuarterGrain,
    salt: Long,
    depth: Int,
    roll: (Long, Long) -> Double,
    into: MutableList<List<Vec2d>>
  ) {
    val area = ConvexPolygons.area(polygon)
    if (depth >= MAX_DEPTH || area <= grain.minLotArea * 2.0) {
      if (area >= grain.minLotArea) into.add(polygon)
      return
    }

    val key = salt * 131 + depth

    // Near a half, spread by the quarter's own size chaos: an even cut gives a row of identical plots, and the
    // spread is what makes a street of houses look like several owners rather than one developer.
    val ratio = 0.5 + (roll(key, RATIO_SALT) - 0.5) * grain.sizeChaos * RATIO_SPREAD

    // Away from square by the quarter's grid chaos. Zero is a surveyed grid; the cap is a little under a sixth of
    // a turn, past which a cut starts producing wedges rather than plots.
    val skew = (roll(key, SKEW_SALT) - 0.5) * 2.0 * grain.gridChaos * MAX_SKEW

    val halves = ConvexPolygons.bisectLongestEdge(polygon, ratio, skew, ALLEY_WIDTH)
    if (halves == null) {
      if (area >= grain.minLotArea) into.add(polygon)
      return
    }

    cut(halves.left, grain, key, depth + 1, roll, into)
    cut(halves.right, grain, key, depth + 1, roll, into)
  }

  /**
   * A finished leaf as a [Lot].
   *
   * The orientation is the part worth reading. `inwards` has to point from the street the plot fronts *into* the
   * block, because everything downstream reads it that way - `Zoning.buildingFor` runs an ordinary house's long
   * axis along it so the town reads as a row of narrow gables, and the door faces back down it. It is taken
   * perpendicular to the leaf's own longest edge, then *signed* by the patch boundary: a leaf on the edge of the
   * block faces the street outside it, and a leaf in the middle faces away from the nearest edge, which is the
   * best available answer for a plot whose frontage is an interior alley.
   */
  private fun lotOf(
    patch: TownPatch,
    leaf: List<Vec2d>,
    rankFor: (edge: Int) -> Int,
    distanceAt: (Vec2d) -> Double,
    lotStep: Double,
    maxHalfFrontage: Double,
    maxHalfDepth: Double,
    minHalfFrontage: Double,
    minHalfDepth: Double
  ): Lot? {
    val extent = ConvexPolygons.orientedExtent(leaf) ?: return null
    val centre = extent.centre

    val nearest = nearestEdge(patch.polygon, centre) ?: return null
    val outward = (centre - nearest.point)
    if (outward.lengthSquared < 1e-9) return null

    // Perpendicular to the frontage, turned to point away from the patch edge - i.e. into the block.
    val across = extent.along.perpendicular()
    val inwards = if ((across dot outward) < 0.0) across else -across

    // Re-measured in the plot's own frame rather than reusing the extent's, because `inwards` is a rotation of
    // `along` and the two half-extents swap with it.
    var halfFrontage = 0.0
    var halfDepth = 0.0
    for (v in leaf) {
      val d = v - centre
      halfFrontage = max(halfFrontage, kotlin.math.abs(d dot inwards.perpendicular()))
      halfDepth = max(halfDepth, kotlin.math.abs(d dot inwards))
    }
    if (halfFrontage <= 0.0 || halfDepth <= 0.0) return null

    // Capped, so that a big block leaf becomes a big plot with a garden rather than one enormous house. Without
    // this a park or a citadel patch - which are deliberately coarse-grained - produced leaves of two thousand
    // square metres, and `Zoning` filled 81 per cent of one with a single building: a sixty-four metre cottage.
    // The leftover is yard, which is what the extra ground on a large plot has always actually been.
    halfFrontage = min(halfFrontage, maxHalfFrontage)
    halfDepth = min(halfDepth, maxHalfDepth)

    // Snapped to a step, which removes the slivers the last cut leaves and would let a modular mesh set fit a plot
    // later without the generator having to be changed for it. Never snapped up: a plot that grew would overlap
    // the alley it was cut from.
    //
    // Quantising the **full** extent and halving, not the half-extent. Flooring a half-extent to the step throws
    // away up to a whole step off each side - a plot 8 m across came back 5 m across, a 38 per cent cut - and the
    // town's smallest buildings came out as 41 m² sheds because of it.
    halfFrontage = quantiseDown(halfFrontage * 2.0, lotStep) * 0.5 * LOT_GAP
    halfDepth = quantiseDown(halfDepth * 2.0, lotStep) * 0.5

    // Against the finished half-extents, after the cap, the step and the gap, because those are what the
    // building is actually sized from. `quantiseDown` floors at half a step, so without this a leaf that the
    // minimum-area test passed as a long sliver came back as a plot 1.15 m across.
    if (halfFrontage < minHalfFrontage || halfDepth < minHalfDepth) return null

    return Lot(
      centre = centre,
      inwards = inwards,
      halfFrontage = halfFrontage,
      halfDepth = halfDepth,
      streetRank = rankFor(nearest.edge),
      // At the plot's front, on the street, for the same reason `LotPlanner` measures it there.
      fromMarket = distanceAt(centre - inwards * halfDepth)
    )
  }

  private class Nearest(val edge: Int, val point: Vec2d, val distance: Double)

  private fun nearestEdge(polygon: List<Vec2d>, to: Vec2d): Nearest? {
    if (polygon.size < 3) return null

    var best: Nearest? = null
    for (i in polygon.indices) {
      val a = polygon[i]
      val b = polygon[(i + 1) % polygon.size]
      val ab = b - a
      val lengthSq = ab.lengthSquared
      val t = if (lengthSq <= 0.0) 0.0 else (((to - a) dot ab) / lengthSq).coerceIn(0.0, 1.0)
      val point = a + ab * t
      val distance = to.distanceTo(point)
      if (best == null || distance < best.distance) best = Nearest(i, point, distance)
    }
    return best
  }

  /** Largest multiple of [step] not exceeding [value]. Half a step is the floor, so a plot cannot vanish. */
  private fun quantiseDown(value: Double, step: Double): Double {
    if (step <= 0.0) return value
    val steps = kotlin.math.floor(value / step)
    return max(step * 0.5, steps * step)
  }

  /**
   * Metres of alley left between two halves of a cut.
   *
   * The gap between neighbouring plots, and therefore between neighbouring buildings. Narrow: this is the space
   * between the gable ends of two houses in a row, not a street - the streets are the patch edges, which the block
   * has already been set back from.
   */
  const val ALLEY_WIDTH = 1.6

  /** Same gap between a plot and its neighbour that `LotPlanner` leaves, so the two paths produce like plots. */
  private const val LOT_GAP = 0.92

  /**
   * Deepest the recursion may go.
   *
   * A backstop rather than an expectation: each cut roughly halves the area, so a patch of a few thousand square
   * metres reaches a two-hundred-metre plot in four or five cuts. Twelve is where a pathological patch is called
   * off, and it is called off with its pieces rather than an exception.
   */
  private const val MAX_DEPTH = 12

  /** How far from an even split the size chaos may push a cut. At one the cut could reach a patch's own corner. */
  private const val RATIO_SPREAD = 0.55

  /** Most a cut may turn from square, in radians. A little under a sixth of a turn. */
  private const val MAX_SKEW = 0.55

  private const val RATIO_SALT = 0x71L
  private const val SKEW_SALT = 0x72L
  private const val EMPTY_SALT = 0x73L
}
