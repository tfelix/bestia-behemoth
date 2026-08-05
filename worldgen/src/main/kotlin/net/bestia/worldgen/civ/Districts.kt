package net.bestia.worldgen.civ

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.ConvexPolygons
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

  /**
   * Seed for the quarter's own name. Zero where a district has none.
   *
   * A name rather than a label, generated the way every other name in the world is - `history/Names.place` from a
   * seed, so it is a pure function of the world seed and nothing has to store a string. Only the *designed*
   * districts carry one: a cluster of workshops that happened to end up next to each other is a description of
   * the map, not a place anybody named.
   */
  const val NAME_SEED = "name_seed"
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
  FARMLAND,

  // The kinds below are *chosen* rather than inferred: they come from `Quarters`, which assigns one to each patch
  // of a town's core, and no `BuildingFunction` maps onto them. Appended rather than inserted because the ordinal
  // is stored in `DistrictChannels.KIND` - the same rule `Biome` and `BuildingFunction` follow.

  /** Small crooked plots packed to their edges, on whatever ground nothing else wanted. */
  SLUM,

  /** Large regular plots with gardens. What wealth looks like from above. */
  PATRICIATE,

  /** A green the town never built on, usually because the ground made it awkward. */
  PARK,

  /** Barracks and a yard to drill in, near the edge they defend. */
  MILITARY,

  /** A keep and its bailey, on compact defensible ground at the town's edge. */
  CITADEL,

  /** The crowd of inns and smithies around a way in. Named for the gate, not for what is made there. */
  GATE
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

  /**
   * Districts for a town with a patched core: one per patch, and the patch *is* the district.
   *
   * No clustering, no hull, no margin pushed outwards to reach the street - a patch was chosen as a quarter before
   * anything was built in it, a street runs along each of its edges by construction, and its blocks were cut to
   * that quarter's own grain. So the polygon is already the answer, and every compromise the inferred path makes
   * disappears: no corner claimed between the arms of an L, no two quarters overlapping, and the district holds
   * the buildings because the buildings were cut out of it.
   *
   * A patch that ended up with no buildings still gets no district. Not because the polygon is wrong - it is the
   * right shape - but because `Invariants.checkDistrictsHoldTheirBuildings` and the name both want a quarter to be
   * somewhere people are, and an empty patch is ground the town did not use.
   */
  fun ofPatches(
    patches: List<TownPatch>,
    quarters: List<DistrictKind>,
    buildings: List<Building>,
    settlement: Int,
    settlementNameSeed: Long,
    cultureIndex: Int,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val out = ArrayList<VectorFeature>(patches.size)

    // Names are chosen here rather than left to whoever reads the seed, because uniqueness is a property of the
    // *set* and a reader only ever sees one. A distinct seed per patch is not enough on its own: `Names.place`
    // draws from a finite pool of stems and suffixes, so a city with two dozen quarters reliably produced two
    // Millwicks and two Dunleighs. Re-rolling until the name is new costs a hash and makes the gazetteer read like
    // one place instead of a table with a bug in it.
    val taken = HashSet<String>()

    for ((index, patch) in patches.withIndex()) {
      val kind = quarters.getOrNull(index) ?: continue

      // Counted by containment rather than tracked through the subdivision, because the buildings that ended up
      // here are not exactly the plots that were cut here: a plot can be rejected for its ground, and `Zoning`
      // fills to a cap in land-value order which stops somewhere in the middle of the town.
      val held = buildings.count { ConvexPolygons.contains(patch.polygon, it.centre) }
      // The same floor the inferred path uses, and for the same reason: below it a "quarter" is a house and its
      // neighbours. A patch under it is ground the town did not use - a park, a patch a river cut to a sliver, or
      // somewhere the building cap never reached - and it gets no district rather than an empty one.
      if (held < MIN_BUILDINGS) continue

      // `runCatching` for the reason every producer of a ring has one: a patch cut by a river to a sliver is under
      // the degeneracy floor, and that is a district the town does not get rather than a stage that throws on the
      // two hundredth world of a sweep.
      val ring = runCatching { Ring(simplify(patch.polygon, Ring.MAX_VERTICES)) }.getOrNull() ?: continue

      val nameSeed = distinctNameSeed(settlementNameSeed, index, kind, cultureIndex, taken)

      out.add(
        AreaFeature(
          id = nextId(),
          kind = FeatureKind.DISTRICT,
          ring = ring,
          profile = null,
          perimeter = StationTable.Builder(ring.vertexCount, periodic = true)
            .channel(DistrictChannels.SETTLEMENT) { settlement.toDouble() }
            .channel(DistrictChannels.KIND) { kind.ordinal.toDouble() }
            .channel(DistrictChannels.BUILDINGS) { held.toDouble() }
            .channel(DistrictChannels.NAME_SEED) { nameSeed.toDouble() }
            .build()
        )
      )
    }

    return out
  }

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

  /**
   * A name seed for one quarter that no earlier quarter of the same town already used.
   *
   * Seeded off the settlement's own name seed, so a quarter's name belongs to its town's naming and is a pure
   * function of the world seed. The attempt counter goes into the hash rather than being stored, so the seed that
   * comes out is the whole answer and a reader needs nothing but [Names.place] to get the same string back.
   *
   * Bounded, and it gives up by taking the last seed it tried: a settlement whose quarters outnumber the usable
   * names would otherwise spin, and two identically named quarters are a far smaller problem than a stage that
   * does not finish.
   */
  private fun distinctNameSeed(
    settlementNameSeed: Long,
    index: Int,
    kind: DistrictKind,
    cultureIndex: Int,
    taken: MutableSet<String>
  ): Long {
    var seed = 0L
    for (attempt in 0 until NAME_ATTEMPTS) {
      seed = GenRng.hash(settlementNameSeed, index.toLong(), kind.ordinal.toLong(), attempt.toLong())
      // Through the same lossy `Double` hop the channel will make, so the name checked here is the name a reader
      // gets. Checking the full-width seed instead would let a collision through whenever two seeds differ only in
      // the low bits a station channel cannot hold.
      val name = Names.place(seed.toDouble().toLong(), cultureIndex)
      if (taken.add(name)) return seed
    }
    return seed
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

  /** Re-rolls allowed while looking for an unused quarter name. Generous; a town has at most a few dozen. */
  private const val NAME_ATTEMPTS = 48
}
