package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.ConvexPolygons
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One patch of a town's core: a convex polygon, the site it grew from, and who it borders.
 *
 * A patch is the unit a *quarter* is assigned to and a unit blocks are cut from. It is not a stored feature - a
 * `DISTRICT` is made from one later - so it stays a plain vertex list rather than a [net.bestia.worldgen.vector.Ring].
 */
internal class TownPatch(
  val polygon: List<Vec2d>,
  /** The Voronoi site. Inside the polygon by construction, which a centroid of a clipped cell need not be. */
  val site: Vec2d,
  /**
   * The patch across each edge, by edge index, or `-1` where there is none.
   *
   * Per edge rather than a set of neighbours, because the question every consumer actually asks is about an edge:
   * what rank of street runs along *this* side, and is *this* side the edge of the built-up area. A set answers
   * neither without searching for the edge again.
   */
  val edgeNeighbour: IntArray
) {
  val centroid: Vec2d get() = ConvexPolygons.centroid(polygon)
  val area: Double get() = ConvexPolygons.area(polygon)
  val compactness: Double get() = ConvexPolygons.compactness(polygon)

  /** True where at least one edge has no patch across it: this patch is on the core's outline. */
  val onOutline: Boolean get() = edgeNeighbour.any { it < 0 }

  val neighbours: List<Int> get() = edgeNeighbour.filter { it >= 0 }.distinct()
}

/**
 * Partitions a town's core into patches, by a Voronoi diagram built from half-plane clipping.
 *
 * ### Why this can exist when blocks could not
 *
 * `Lot`'s KDoc records that `street graph -> faces -> blocks -> subdivide` was built and deleted: a face exists
 * only because some cycle of streets closes, so one river through a town broke every ring's cycle and took a city
 * from 574 plots to 68. The lesson taken from it - do not make a plot's existence depend on a graph cycle - is
 * kept here, and this is why it is kept:
 *
 * **A Voronoi diagram is a partition by construction.** It has no cycles to break and no traversal to get lost
 * in. Every point of the core belongs to exactly one patch because it is nearer that patch's site than any
 * other, and that remains true whatever the terrain does. A river through the town costs the lots it physically
 * crosses - each is still tested individually - and cannot cost a patch, its neighbours, or the whole town.
 *
 * ### Why half-plane clipping rather than a Voronoi algorithm
 *
 * Fortune's sweep or a Delaunay dual is the right answer at scale and is a few hundred lines with its own
 * degeneracy handling. A cell here is `intersection over j of { p : |p - s_i| <= |p - s_j| }`, which is a fold of
 * [ConvexPolygons.clipByHalfPlane] over the neighbouring sites and is exact. With at most a few dozen sites per
 * town the quadratic cost is nothing, and there is no tie-breaking to get wrong on cocircular sites - a
 * degenerate case simply produces a cell with a very short edge in it.
 *
 * ### The guard ring
 *
 * The cells of the sites on the convex hull of a point set are *unbounded*, and clipping them to a big box gives
 * long slivers reaching to the box's corners. So a ring of sites is sown outside the core, their cells discarded:
 * every real cell is then bounded by real geometry, and the outline of the union is the shape the sites imply
 * rather than the shape of the box.
 */
internal object TownPatches {

  /**
   * Patches covering the core of a town.
   *
   * @param coreRadius reach of the core about the town centre. The suburbs beyond it keep the grown-street path.
   * @param wantedPatches how many patches to aim for. Fewer than this come back where ground is unbuildable.
   * @param channels river centrelines, so that no patch straddles water. See [cutAtChannels].
   */
  fun of(
    frame: TownFrame,
    /**
     * The core, as a polygon rather than a radius.
     *
     * A polygon and not a radius because the town's edge is not a circle any more, and seeding sites inside one
     * threw that away: the first version of this took a radius, and the render showed a plainly elongated town
     * with a plainly round honeycomb of quarters inside it. The core is the town's own outline scaled down, so the
     * quarters are elongated the same way the town is.
     */
    core: List<Vec2d>,
    wantedPatches: Int,
    channels: List<Polyline>,
    /** Keyed roll, `(salt...) -> [0,1)`. */
    roll: (Long, Long) -> Double
  ): List<TownPatch> {
    if (wantedPatches < 1 || core.size < 3) return emptyList()

    val coreRadius = ConvexPolygons.equivalentRadius(core)
    if (coreRadius <= 0.0) return emptyList()

    val sites = sitesIn(frame, core, wantedPatches, roll)
    if (sites.size < 2) return emptyList()

    val guards = guardRing(frame.centre, core, sites.size)
    var all = sites + guards

    // Lloyd relaxation, and it is not cosmetic: Poisson-disk sites are evenly *spaced* but their cells vary
    // widely in shape, and a patch that is a long sliver subdivides into a row of slivers. Moving each site to
    // its own cell's centroid regularises the cells without regularising them into a grid. The guards are held
    // fixed - relaxing them would let the ring collapse inward and eat the core.
    repeat(RELAXATIONS) {
      val moved = ArrayList<Vec2d>(all.size)
      for (i in sites.indices) {
        val cell = cellOf(all, i, coreRadius * GUARD_REACH)
        moved.add(if (cell.size >= 3) ConvexPolygons.centroid(cell) else all[i])
      }
      all = moved + guards
    }

    val cells = ArrayList<List<Vec2d>>(sites.size)
    val kept = ArrayList<Vec2d>(sites.size)
    for (i in sites.indices) {
      val cell = ConvexPolygons.clean(cellOf(all, i, coreRadius * GUARD_REACH))
      if (cell.size < 3) continue
      if (ConvexPolygons.area(cell) < MIN_PATCH_AREA) continue

      // The site rather than the centroid, because the site is what the cell was built around and is inside it.
      // A patch whose own site is on unbuildable ground is a patch nothing can be built in.
      if (!frame.buildable(all[i])) continue

      cells.add(cell)
      kept.add(all[i])
    }

    val cut = cutAtChannels(cells, kept, channels)
    return assemble(cut, kept)
  }

  /**
   * How many patches a core of this size wants, from the buildings it has to hold.
   *
   * Derived from the building count rather than from the area so that the *grain* of a town is constant: a patch
   * is a block of a couple of dozen plots whatever the size of the settlement, so a bigger town gets more
   * patches rather than bigger ones. Bounded at both ends - below the floor a "partition" is one shape and the
   * whole idea buys nothing, and above the ceiling the quadratic cell construction starts to be worth noticing
   * in the most expensive stage in the pipeline.
   */
  fun countFor(buildings: Int): Int =
    (buildings / LOTS_PER_PATCH).coerceIn(MIN_PATCHES, MAX_PATCHES)

  /**
   * Poisson-disk sites inside the core, on buildable ground.
   *
   * Poisson rather than the reference generator's spiral. A spiral gives compact central cells and increasingly
   * ragged outer ones, which is a real property of a town grown from a crossroads - but it also fixes the site
   * count and the spacing to one another, and here the count comes from the population while the spacing has to
   * follow the core. Poisson-with-relaxation gets the compact middle from the relaxation instead, and reuses
   * `fields/PoissonDisk` rather than adding a second point-set generator to the module.
   */
  private fun sitesIn(
    frame: TownFrame,
    core: List<Vec2d>,
    wanted: Int,
    roll: (Long, Long) -> Double
  ): List<Vec2d> {
    // Spacing that puts `wanted` sites in the core's area, then a little tighter: Poisson-disk rejects on its
    // own and consistently lands under a spacing's nominal count, so aiming exactly at it comes back short.
    val spacing = sqrt(ConvexPolygons.area(core) / max(1, wanted)) * SPACING_TIGHTEN

    val reach = ConvexPolygons.reachFrom(core, frame.centre)
    val bounds = Aabb(
      frame.centre.x - reach, frame.centre.y - reach,
      frame.centre.x + reach, frame.centre.y + reach
    )
    // `PoissonDisk` wants a stream rather than a key, because Bridson's front is consumed in an order the stream
    // chooses. Derived from the town's own keyed roll so the stream is still a pure function of the settlement.
    val rng = GenRng((roll(0L, SITE_SALT) * (1L shl 52)).toLong())

    return PoissonDisk.sample(bounds, spacing, rng)
      // Against the core's own outline, so an elongated town gets elongated quarters. A ray test rather than
      // `ConvexPolygons.contains`, because the core is a scaled copy of a warped ring and need not be convex.
      .filter { insideRing(core, it) && frame.buildable(it) }
      // Sorted so the patch order - and therefore every id, quarter and district derived from it - is a function
      // of geometry rather than of the sampler's internal visit order.
      .sortedWith(compareBy({ it.x }, { it.y }))
  }

  /**
   * Sites sown outside the core to bound the cells of the ones inside it. Never returned as patches.
   *
   * Spaced to about the same density as the real sites, so the outer cells are cut off at a comparable scale to
   * the inner ones. Too few guards and the edge patches come out much larger than the middle ones, which reads
   * as a town with a dense core and four enormous fields around it.
   */
  private fun guardRing(centre: Vec2d, core: List<Vec2d>, siteCount: Int): List<Vec2d> {
    val count = max(GUARD_MINIMUM, (siteCount * GUARD_SHARE).toInt())
    // Resampled *along the core's own outline* pushed outward, not around a circle. A circular guard ring around
    // an elongated core cuts the cells at its two ends much harder than the ones along its flanks, which pulls the
    // whole partition back towards round - which is exactly what this change is undoing.
    val outline = ConvexPolygons.scaledAbout(core, centre, GUARD_RING)
    val out = ArrayList<Vec2d>(count)
    for (i in 0 until count) {
      out.add(alongOutline(outline, i.toDouble() / count))
    }
    return out
  }

  /** A point a given share of the way round a closed vertex list, by arc length. */
  private fun alongOutline(outline: List<Vec2d>, turn: Double): Vec2d {
    var total = 0.0
    for (i in outline.indices) total += outline[i].distanceTo(outline[(i + 1) % outline.size])
    if (total <= 0.0) return outline.first()

    var target = turn * total
    for (i in outline.indices) {
      val a = outline[i]
      val b = outline[(i + 1) % outline.size]
      val length = a.distanceTo(b)
      if (target <= length) return a.lerp(b, if (length > 0.0) target / length else 0.0)
      target -= length
    }
    return outline.last()
  }

  /**
   * Crossing-number test against a closed vertex list, for a shape that need not be convex.
   *
   * The core is `TownFrame.boundary` scaled down, and a warped ring is star-shaped rather than convex, so
   * `ConvexPolygons.contains` would reject points in the bays. `Ring.contains` is the general answer and goes
   * through fixed point because two chunks have to agree on it; nothing here is re-derived per chunk, so this
   * stays a plain float test.
   */
  private fun insideRing(ring: List<Vec2d>, point: Vec2d): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
      val a = ring[i]
      val b = ring[j]
      if ((a.y > point.y) != (b.y > point.y) &&
        point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
      ) {
        inside = !inside
      }
      j = i
    }
    return inside
  }

  /** The Voronoi cell of site [index], as the intersection of its bisector half-planes. */
  private fun cellOf(sites: List<Vec2d>, index: Int, reach: Double): List<Vec2d> {
    val site = sites[index]
    var cell = ConvexPolygons.regular(site, reach, SEED_POLYGON_SIDES)

    for (j in sites.indices) {
      if (j == index) continue
      val other = sites[j]
      val delta = other - site
      val distanceSq = delta.lengthSquared
      if (distanceSq < 1e-9) continue
      // Sites further than twice the current reach cannot bound this cell, and skipping them is what keeps the
      // quadratic loop cheap on the larger towns.
      if (distanceSq > 4.0 * reach * reach) continue

      // The perpendicular bisector: through the midpoint, normal pointing at the other site.
      cell = ConvexPolygons.clipByHalfPlane(cell, site + delta * 0.5, delta.normalized())
      if (cell.size < 3) return emptyList()
    }

    return cell
  }

  /**
   * Trims any patch a river runs through back to the bank its own site is on.
   *
   * Without this a patch straddles the channel, and everything downstream inherits the mistake: the block
   * subdivision cuts plots across open water for `frame.buildable` to reject one at a time, the quarter reads as
   * one place when it is two, and a `DISTRICT` polygon claims the river as part of a neighbourhood.
   *
   * The channel is treated as a straight line locally - the tangent where it passes the patch - which at a
   * hundred-odd metres across is well inside the error of the channel's own vertex spacing. Modelling the bend
   * would need a non-convex cut and would buy nothing a block boundary can express.
   */
  private fun cutAtChannels(
    cells: List<List<Vec2d>>,
    sites: List<Vec2d>,
    channels: List<Polyline>
  ): List<List<Vec2d>> {
    if (channels.isEmpty()) return cells

    val out = ArrayList<List<Vec2d>>(cells.size)
    for (i in cells.indices) {
      var cell = cells[i]
      val site = sites[i]

      for (channel in channels) {
        if (cell.size < 3) break
        val reach = ConvexPolygons.reachFrom(cell, site)
        if (!channel.bbox.expanded(reach).contains(site.x, site.y)) continue

        val projection = channel.project(site)
        if (projection.distance > reach) continue

        val tangent = channel.tangentAt(projection.s)
        if (tangent.lengthSquared < 0.5) continue

        // Normal pointing from the channel towards this patch's own side, so the half-plane kept is the bank the
        // site is on. A site *on* the centreline has no side; those were already rejected as unbuildable.
        val away = (site - projection.point)
        if (away.lengthSquared < 1e-9) continue
        val normal = tangent.perpendicular().let { if ((it dot away) > 0.0) -it else it }

        cell = ConvexPolygons.clipByHalfPlane(cell, projection.point, normal)
      }

      out.add(if (cell.size >= 3 && ConvexPolygons.area(cell) >= MIN_PATCH_AREA) cell else emptyList())
    }
    return out
  }

  /**
   * Builds the patch list and its adjacency.
   *
   * Adjacency is measured rather than recorded during clipping, and that is deliberate: a clip that survives the
   * construction can still be cut away by a later one, so "this bisector was applied" is not the same claim as
   * "this bisector bounds the final cell". The test is on the finished geometry - an edge whose midpoint is
   * equidistant from two sites lies on their bisector, so those two patches border each other there.
   */
  private fun assemble(cells: List<List<Vec2d>>, sites: List<Vec2d>): List<TownPatch> {
    val live = cells.indices.filter { cells[it].size >= 3 }
    val renumbered = HashMap<Int, Int>()
    live.forEachIndexed { newIndex, old -> renumbered[old] = newIndex }

    val out = ArrayList<TownPatch>(live.size)
    for (old in live) {
      val cell = cells[old]
      val site = sites[old]

      // An edge with no patch across it borders either a guard cell or a river cut, and either way it is the edge
      // of the built-up core - which is what a gate, a wall and a farm all want to know.
      val across = IntArray(cell.size) { -1 }

      for (e in cell.indices) {
        val midpoint = cell[e].lerp(cell[(e + 1) % cell.size], 0.5)
        val own = midpoint.distanceTo(site)

        for (other in live) {
          if (other == old) continue
          if (abs(midpoint.distanceTo(sites[other]) - own) > ADJACENCY_TOLERANCE) continue
          across[e] = renumbered[other] ?: -1
          break
        }
      }

      out.add(TownPatch(polygon = cell, site = site, edgeNeighbour = across))
    }

    return out
  }

  /**
   * Share of a town's boundary reach that the patched core occupies.
   *
   * The rest is suburbs, laid by the grown-street path that every settlement used before patches existed. The
   * reference city this is modelled on has roughly two thirds of its built area outside its wall, strung along
   * the roads and the river rather than filling a ring - and that shape is what street-fronted plots produce
   * naturally, so the two methods each keep the half of the town they are good at.
   */
  const val CORE_SHARE = 0.6

  /** Plots a patch is meant to hold. What fixes the grain of a town independently of its size. */
  private const val LOTS_PER_PATCH = 26

  private const val MIN_PATCHES = 6
  private const val MAX_PATCHES = 48

  /** Below this a cell is a sliver between two neighbours rather than a block. Square metres. */
  private const val MIN_PATCH_AREA = 400.0

  private const val RELAXATIONS = 3

  /** Radius of the guard ring, as a share of the core's. Far enough not to cut into the outer real cells. */
  private const val GUARD_RING = 1.28

  /** Reach of the polygon a cell is clipped out of, as a share of the core's. Must exceed [GUARD_RING]. */
  private const val GUARD_REACH = 1.7

  private const val GUARD_MINIMUM = 10
  private const val GUARD_SHARE = 0.7

  /** Sides of the seed polygon. Enough that an unbounded direction is cut by a facet, not by a corner. */
  private const val SEED_POLYGON_SIDES = 16

  /** Poisson spacing is multiplied by this, because the sampler lands under a spacing's nominal count. */
  private const val SPACING_TIGHTEN = 0.86

  /** Metres of slack in the equidistance test for a shared edge. A tenth of a metre of geometry, not of town. */
  private const val ADJACENCY_TOLERANCE = 0.1

  private const val SITE_SALT = 0x61L
}
