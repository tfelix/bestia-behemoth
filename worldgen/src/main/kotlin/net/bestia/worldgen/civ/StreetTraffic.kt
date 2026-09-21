package net.bestia.worldgen.civ

import net.bestia.worldgen.fields.DoubleIntHeap
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.atan2
import kotlin.math.floor

/**
 * How wide a street is, decided by what uses it.
 *
 * A street's rank was the generation of growth that made it: a radial out of the centre was rank 0 because it
 * was drawn first, and a branch off it rank 1 because it was drawn second. So the widest street in a town was
 * the earliest one rather than the one anybody walks down, and a town read as a set of streets with no
 * hierarchy between them - which is most of the difference between a generated plan and a surveyed one.
 *
 * Here the rank comes from through traffic: every way into the settlement is routed to every other and to the
 * market, and an edge's rank follows how much of that passes over it. What comes out is one continuous wide
 * route through the town - the thing a reader's eye follows first on a real map - with everything else
 * subordinate to it, and it needs no knowledge of how any street was produced. The same pass therefore ranks a
 * grown street, a grid avenue, a patch edge and a village's own way on one scale.
 *
 * ### Why the bands are shares of length rather than of the busiest street
 *
 * Traffic is distributed very unevenly and its top value depends on how many ways reach a settlement, so
 * "rank 0 is anything over half the maximum" gives a town with two gates a different hierarchy from one with
 * six, for a reason that is about the region rather than about the town. Taking the bands as shares of the
 * town's own street *length* fixes the proportions instead: the same fraction of a settlement is arterial
 * wherever it stands, which is what makes the widths comparable between one town and the next.
 */
internal object StreetTraffic {

  /**
   * [graph] with every rank re-derived from the traffic it carries.
   *
   * A settlement with nothing to route between - one junction, or a single stub - comes back unchanged rather
   * than with every street at one rank.
   */
  fun ranked(graph: StreetGraph, frame: TownFrame): StreetGraph {
    if (graph.edges.size < 2) return graph

    val ways = waysIn(graph, frame)
    if (ways.size < 2) return graph

    val load = DoubleArray(graph.edges.size)
    val market = nearest(graph, frame.centre)

    for (from in ways) {
      val came = routesFrom(graph, from)
      // To the market as well as to every other way in, because a settlement is somewhere people stop at and
      // not only somewhere they pass through - without it a town nobody crosses has no high street at all.
      for (to in ways) {
        if (to > from) walk(graph, came, to, load)
      }
      if (market >= 0) walk(graph, came, market, load)
    }

    return StreetGraph(graph.nodes, rankedBy(graph, load))
  }

  /** The junction standing nearest the middle: where everything that is not passing through is going. */
  private fun nearest(graph: StreetGraph, to: Vec2d): Int {
    var best = -1
    var closest = Double.MAX_VALUE
    for (node in graph.nodes.indices) {
      if (graph.degreeOf(node) == 0) continue
      val away = graph.nodes[node].distanceSquaredTo(to)
      if (away >= closest) continue
      closest = away
      best = node
    }
    return best
  }

  /**
   * Where the settlement is entered: the outermost node on each bearing.
   *
   * By sector rather than by a distance threshold, so a settlement strung out along one axis still offers ways
   * in from its flanks - a threshold would return only the two ends of the long axis and route every path down
   * the same street.
   */
  private fun waysIn(graph: StreetGraph, frame: TownFrame): List<Int> {
    val best = IntArray(SECTORS) { -1 }
    val furthest = DoubleArray(SECTORS)

    for (node in graph.nodes.indices) {
      // A node no street leaves is not a way in; it is a weld left over from planarisation.
      if (graph.degreeOf(node) == 0) continue

      val offset = graph.nodes[node] - frame.centre
      val away = offset.length
      if (away < frame.builtRadius * EDGE_SHARE) continue

      val turn = (atan2(offset.y, offset.x) / (2.0 * Math.PI) + 1.0) % 1.0
      val sector = floor(turn * SECTORS).toInt().coerceIn(0, SECTORS - 1)
      if (away <= furthest[sector]) continue

      furthest[sector] = away
      best[sector] = node
    }

    val bySector = best.filter { it >= 0 }
    if (bySector.size >= 2) return bySector

    // A settlement whose streets all sit inside the threshold - a core laid well within its own boundary - has
    // no way in by the sector test, and returning none leaves every rank untouched. The two furthest junctions
    // are the ends of the longest way through it, which is the least this can honestly call a through route.
    return graph.nodes.indices
      .filter { graph.degreeOf(it) > 0 }
      .sortedByDescending { graph.nodes[it].distanceSquaredTo(frame.centre) }
      .take(2)
  }

  /** Dijkstra from one node, returning the edge each node was reached by, or -1. */
  private fun routesFrom(graph: StreetGraph, source: Int): IntArray {
    val distance = DoubleArray(graph.nodes.size) { Double.MAX_VALUE }
    val came = IntArray(graph.nodes.size) { -1 }
    val settled = BooleanArray(graph.nodes.size)
    val open = DoubleIntHeap(graph.nodes.size.coerceAtLeast(16))

    distance[source] = 0.0
    open.push(0.0, source)

    while (!open.isEmpty) {
      val node = open.pop()
      if (settled[node]) continue
      settled[node] = true

      for (edge in graph.edgesAt(node)) {
        val other = graph.across(edge, node)
        val step = distance[node] + graph.lengthOf(edge)
        if (step >= distance[other]) continue

        distance[other] = step
        came[other] = edge
        open.push(step, other)
      }
    }

    return came
  }

  /** Adds one journey's worth of load to every edge on the route back from [target]. */
  private fun walk(graph: StreetGraph, came: IntArray, target: Int, load: DoubleArray) {
    var at = target
    var guard = graph.edges.size + 1
    while (guard-- > 0) {
      val edge = came[at]
      if (edge < 0) return
      load[edge] += 1.0
      at = graph.across(edge, at)
    }
  }

  /**
   * Ranks, taken as shares of the settlement's own street length in descending order of load.
   *
   * Ties broken by edge index so that two streets carrying the same traffic rank in a stable order rather than
   * in whatever order the sort happened to leave them.
   */
  private fun rankedBy(graph: StreetGraph, load: DoubleArray): List<StreetGraph.Edge> {
    val order = graph.edges.indices.sortedWith(compareByDescending<Int> { load[it] }.thenBy { it })
    val total = graph.edges.indices.sumOf { graph.lengthOf(it) }
    if (total <= 0.0) return graph.edges

    val ranks = IntArray(graph.edges.size)
    var walked = 0.0
    for (edge in order) {
      val share = walked / total
      ranks[edge] = when {
        // An edge nothing routes over is a back lane whatever its share of the length says.
        load[edge] <= 0.0 -> LANE_RANK
        share < ARTERIAL_SHARE -> 0
        share < SECONDARY_SHARE -> 1
        else -> LANE_RANK
      }
      walked += graph.lengthOf(edge)
    }

    return graph.edges.mapIndexed { i, edge -> StreetGraph.Edge(edge.a, edge.b, ranks[i]) }
  }

  /** Bearings a settlement is entered on. Twelve is every thirty degrees, which no town has two gates inside. */
  private const val SECTORS = 12

  /** How far out a node has to stand to count as a way in, as a share of the built radius. */
  private const val EDGE_SHARE = 0.55

  /** Share of a settlement's street length that is arterial, then the share that is at least secondary. */
  private const val ARTERIAL_SHARE = 0.22
  private const val SECONDARY_SHARE = 0.55

  /** Ranks stop here: nothing a town emits reaches `StreetParams.RANK_SPAN`. */
  private const val LANE_RANK = 2
}
