package net.bestia.worldgen.civ

import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs

/** Station channels on a [FeatureKind.DISTRICT]. */
object DistrictChannels {
  /** `SettlementChannels.INDEX` of the settlement this district belongs to. */
  const val SETTLEMENT = "settlement"

  /** Ordinal of [DistrictKind]. */
  const val KIND = "kind"

  /** How many buildings the district was grown from. Not a live count - nothing updates it. */
  const val BUILDINGS = "buildings"
}

/**
 * What a group of neighbouring plots amounts to.
 *
 * Coarser than [BuildingFunction] on purpose. A district is a thing a player names - "the craft quarter", "the
 * market" - and a shop, an inn and the market square are one such thing from the outside even though they are
 * three functions inside. The mapping is a `when` with no `else`, so a new building function has to be
 * assigned to a quarter rather than defaulting into the residential one.
 */
enum class DistrictKind {
  /** The market square and the shops and inns around it: where a town's strangers are. */
  MARKET,

  /** Workshops and the stores behind them. Downwind and downstream, because [Zoning] put them there. */
  CRAFT,

  /** Temple and civic buildings - the ones with a broad front onto the street. */
  CIVIC,

  /** Housing, which is most of any town. */
  RESIDENTIAL,

  /** Farmsteads at the edge, where the plots stop being urban. */
  FARMLAND
}

/**
 * Districts: a polygon over a group of plots, and the revival of the town-block idea that was deleted.
 *
 * ### Why this is not `StreetGraph.faces` again
 *
 * Blocks were built once the way the design describes - as the faces of the street graph - and removed. A face
 * exists only because ring streets *close*, so a single river through a town breaks every ring's cycle, and the
 * reference city went from 574 plots to 68 in one commit. Making the existence of a plot depend on a graph
 * cycle is the mistake, and it is not one that better face-finding fixes.
 *
 * A district instead grows *from the plots that exist*: cluster the placed buildings of one quarter by
 * proximity, take the convex hull of their corners, and store it. Nothing about a plot depends on it, so a
 * river through the middle of a town costs the town a district rather than its buildings - which is what the
 * ground would look like anyway.
 *
 * ### The hull, and why convex
 *
 * A convex hull is **simple by construction**, which is the precondition [Ring] enforces and would otherwise be
 * a source of rejected districts: a flood-filled block boundary is a far better fit to the ground and can be
 * self-intersecting, non-manifold or annular, none of which the ring type accepts. The cost is that a district
 * around an L-shaped group of streets claims the corner between the arms. That corner is the *street*, which
 * is the thing a district is around; so the answer is more nearly right than the shape suggests.
 *
 * Two districts may overlap where two quarters interleave. That is left as-is rather than resolved, because
 * quarters genuinely do interleave and the feature machinery already ranks overlapping features by priority.
 */
internal object Districts {

  /**
   * Fewest buildings that make a quarter.
   *
   * Below this it is a house and its neighbours, not a district, and naming it one would put "the craft
   * quarter" on a hamlet with three workshops in it. Measured against the reference world: at 5 a 512 km world
   * gets districts in most of its towns and none in its hamlets, which is the intended shape.
   */
  const val MIN_BUILDINGS = 5

  /**
   * Metres within which two buildings of one quarter are in the same district.
   *
   * A little over two plot frontages, so a gap of one missing plot links and a gap of a whole street does not.
   * Taken from the frontage rather than fixed, because the frontage is the number that decides how far apart
   * two neighbours actually stand.
   */
  const val LINK_FACTOR = 2.6

  /**
   * Metres the hull is pushed outward from its own centroid.
   *
   * A district should contain the street its buildings front onto, and the hull of the *buildings* stops at
   * their front walls. One setback plus half a street is about right and is what this is.
   */
  const val MARGIN = 7.0

  fun of(
    buildings: List<Building>,
    settlement: Int,
    lotFrontage: Double,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val out = ArrayList<VectorFeature>(4)
    val linkRadius = lotFrontage * LINK_FACTOR

    for (kind in DistrictKind.entries) {
      val theirs = buildings.filter { quarterOf(it.function) == kind }
      if (theirs.size < MIN_BUILDINGS) continue

      for (cluster in cluster(theirs, linkRadius)) {
        if (cluster.size < MIN_BUILDINGS) continue

        // `runCatching`, the rule every producer of a ring follows: a cluster strung out along one straight
        // street hulls to a sliver whose area is under the degeneracy floor, and that is a district the town
        // does not get rather than a stage that throws on the two hundredth world of a sweep.
        val ring = runCatching { ringAround(cluster) }.getOrNull() ?: continue

        out.add(
          AreaFeature(
            id = nextId(),
            kind = FeatureKind.DISTRICT,
            ring = ring,
            // No profile: a district is a query surface, not a landform. `AreaFeature` documents this as the
            // reason the parameter is nullable, and it is what keeps districts out of every height path.
            profile = null,
            // Constant around the boundary, and periodic with one station per vertex because that is the
            // contract `AreaFeature` enforces - a table of any other length samples a rotated version of what
            // was written. A district has nothing that varies along its edge; the shape is the whole message.
            perimeter = StationTable.Builder(ring.vertexCount, periodic = true)
              .channel(DistrictChannels.SETTLEMENT) { settlement.toDouble() }
              .channel(DistrictChannels.KIND) { kind.ordinal.toDouble() }
              .channel(DistrictChannels.BUILDINGS) { cluster.size.toDouble() }
              .build()
          )
        )
      }
    }

    return out
  }

  /** Coarse quarter for a building function. Exhaustive, so a new function has to be placed in a quarter. */
  fun quarterOf(function: BuildingFunction): DistrictKind? = when (function) {
    BuildingFunction.MARKET, BuildingFunction.SHOP, BuildingFunction.INN -> DistrictKind.MARKET
    BuildingFunction.CRAFT, BuildingFunction.WAREHOUSE -> DistrictKind.CRAFT
    BuildingFunction.TEMPLE, BuildingFunction.CIVIC -> DistrictKind.CIVIC
    BuildingFunction.RESIDENCE -> DistrictKind.RESIDENTIAL
    BuildingFunction.FARM -> DistrictKind.FARMLAND

    // A wall tower belongs to the circuit, not to a quarter. Null rather than a sixth kind, because a
    // district of fortifications would be a ring around the town that says nothing the wall does not.
    BuildingFunction.FORTIFICATION -> null
  }

  /**
   * Single-linkage clustering by union-find.
   *
   * O(n^2) over the buildings of one quarter of one settlement - a few hundred at the very largest - and the
   * alternative is a grid index whose cell size would be this radius anyway.
   */
  private fun cluster(buildings: List<Building>, radius: Double): List<List<Building>> {
    val parent = IntArray(buildings.size) { it }

    fun find(a: Int): Int {
      var x = a
      while (parent[x] != x) {
        parent[x] = parent[parent[x]]
        x = parent[x]
      }
      return x
    }

    val radiusSq = radius * radius
    for (i in buildings.indices) {
      for (j in i + 1 until buildings.size) {
        val d = buildings[i].centre - buildings[j].centre
        if (d.x * d.x + d.y * d.y <= radiusSq) {
          val a = find(i)
          val b = find(j)
          if (a != b) parent[a] = b
        }
      }
    }

    val groups = LinkedHashMap<Int, MutableList<Building>>()
    for (i in buildings.indices) groups.getOrPut(find(i)) { ArrayList() }.add(buildings[i])
    return groups.values.toList()
  }

  /** The convex hull of every building corner, capped at [Ring.MAX_VERTICES] and pushed out by [MARGIN]. */
  private fun ringAround(cluster: List<Building>): Ring {
    val corners = ArrayList<Vec2d>(cluster.size * 4)
    for (building in cluster) {
      val along = building.bearing * building.halfLength
      val across = building.bearing.perpendicular() * building.halfWidth
      for ((a, b) in CORNERS) corners.add(building.centre + along * a + across * b)
    }

    val hull = simplify(convexHull(corners), Ring.MAX_VERTICES)

    // Push out from the centroid. On a convex polygon this cannot self-intersect: the vertices keep their
    // angular order about an interior point and only their radii grow.
    val cx = hull.sumOf { it.x } / hull.size
    val cy = hull.sumOf { it.y } / hull.size
    val centre = Vec2d(cx, cy)

    return Ring(hull.map { it + (it - centre).normalized() * MARGIN })
  }

  /** Andrew's monotone chain. Returns counter-clockwise, with no collinear vertices and no repeated end. */
  private fun convexHull(points: List<Vec2d>): List<Vec2d> {
    val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
    if (sorted.size < 3) return sorted

    fun half(input: List<Vec2d>): MutableList<Vec2d> {
      val out = ArrayList<Vec2d>()
      for (p in input) {
        while (out.size >= 2 && cross(out[out.size - 2], out[out.size - 1], p) <= 0.0) out.removeAt(out.size - 1)
        out.add(p)
      }
      return out
    }

    val lower = half(sorted)
    val upper = half(sorted.reversed())
    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    return lower + upper
  }

  private fun cross(o: Vec2d, a: Vec2d, b: Vec2d) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

  /**
   * Drops vertices until the ring fits, cheapest first.
   *
   * "Cheapest" is the triangle a vertex makes with its neighbours - the area the hull loses by cutting the
   * corner off. Removing a vertex from a convex polygon leaves it convex, so the result is still a legal ring
   * however many go.
   */
  private fun simplify(hull: List<Vec2d>, limit: Int): List<Vec2d> {
    if (hull.size <= limit) return hull

    val kept = ArrayList(hull)
    while (kept.size > limit) {
      var cheapest = 0
      var least = Double.MAX_VALUE
      for (i in kept.indices) {
        val prev = kept[(i - 1 + kept.size) % kept.size]
        val next = kept[(i + 1) % kept.size]
        val area = abs(cross(prev, kept[i], next)) * 0.5
        if (area < least) {
          least = area
          cheapest = i
        }
      }
      kept.removeAt(cheapest)
    }
    return kept
  }

  private val CORNERS = listOf(1.0 to 1.0, 1.0 to -1.0, -1.0 to -1.0, -1.0 to 1.0)
}
