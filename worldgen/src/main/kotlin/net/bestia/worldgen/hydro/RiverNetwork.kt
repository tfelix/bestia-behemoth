package net.bestia.worldgen.hydro

import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.IntGrid

/**
 * One river reach: the run of channel between two nodes of the river graph.
 *
 * A reach is the unit everything downstream cares about. It is what becomes one vector feature, what
 * carries a discharge and a width, what a road decides whether it can ford, and what a boat can
 * navigate. Splitting the network at confluences rather than storing one polyline per river means the
 * attributes along a reach are genuinely constant in the ways that matter - Strahler order does not
 * change mid-reach, and discharge only grows smoothly.
 */
class Reach(
  val id: Int,
  /**
   * Cell indices from upstream to downstream, at least two.
   *
   * The last entry is the reach's terminus and belongs to whatever it flows into - the confluence
   * below it, a lake, or the sea. Including it is what makes adjacent reaches join up rather than stop
   * a cell short of each other.
   */
  val cells: IntArray,
  val strahler: Int,
  /** The reach this one flows into, or -1 when it ends at standing water or leaves the map. */
  val downstream: Int,
  val endsInLake: Boolean,
  val endsInSea: Boolean
) {
  val cellCount get() = cells.size
  val head get() = cells.first()
  val mouth get() = cells.last()

  override fun toString() = "Reach[$id, ${cells.size} cells, order $strahler]"
}

/** The river graph: reaches, the nodes that join them, and the Strahler order of every channel cell. */
class RiverGraph(
  val reaches: List<Reach>,
  /** Cells where two or more reaches meet, ascending. */
  val confluences: IntArray,
  /** Strahler order per cell; 0 where there is no channel. */
  val strahler: IntArray,
  /** Which cells carry a channel at all. */
  val channel: BooleanArray
) {
  val reachCount get() = reaches.size
}

/**
 * Turns a solved drainage network into a river graph.
 *
 * This is the step the architecture document is emphatic about not skipping. A raster river *mask* is
 * enough to colour a map and useless for anything else: it cannot be smoothed without smearing, cannot
 * be meandered continuously, cannot be queried for "where does this reach cross that road", and cannot
 * carve a channel at voxel resolution. The graph can do all four, and it is the same amount of work to
 * produce.
 */
object RiverNetwork {

  /**
   * @param dischargeThreshold minimum discharge in cubic metres per second for a cell to carry a
   *   channel. Scaled per cell by the caller so arid regions get sparse drainage, which is what makes a
   *   desert look like a desert on the map rather than like a wet region with less rain.
   */
  fun extract(
    network: DrainageNetwork,
    discharge: Grid,
    lakeId: IntGrid,
    dischargeThreshold: (index: Int) -> Double
  ): RiverGraph {
    val size = network.size

    val channel = BooleanArray(size) { i ->
      !network.ocean[i] && lakeId.data[i] == 0 && discharge.data[i] >= dischargeThreshold(i)
    }

    // A channel that starts must carry on to the sea or into a lake. Without this closure a river
    // flowing out of a wet upland into a desert simply stops in mid-plain, because the local threshold
    // rose above its discharge - and then the regression invariant "every river reaches standing water"
    // is violated by a river that in reality would have carried on as a losing stream.
    for (k in network.stack.indices.reversed()) {
      val i = network.stack[k]
      if (!channel[i]) continue
      val r = network.receiver[i]
      if (r != i && !network.ocean[r] && lakeId.data[r] == 0) channel[r] = true
    }

    val strahler = strahlerOrders(network, channel)
    val donors = channelDonorCounts(network, channel)

    // A node is where the graph branches: a source with nothing above it, or a confluence with two or
    // more channels arriving. Everything between two nodes is one reach.
    val isNode = BooleanArray(size) { i -> channel[i] && (donors[i] == 0 || donors[i] >= 2) }

    val reaches = ArrayList<Reach>()
    val reachStartingAt = HashMap<Int, Int>()

    for (start in 0 until size) {
      if (!isNode[start]) continue

      val path = ArrayList<Int>()
      path.add(start)

      var current = start
      var endsInLake = false
      var endsInSea = false

      while (true) {
        val r = network.receiver[current]
        if (r == current) break

        path.add(r)

        if (network.ocean[r]) {
          endsInSea = true
          break
        }
        if (lakeId.data[r] != 0) {
          endsInLake = true
          break
        }
        if (isNode[r]) break

        current = r
      }

      if (path.size < 2) continue

      reachStartingAt[start] = reaches.size
      reaches.add(
        Reach(
          id = reaches.size,
          cells = path.toIntArray(),
          // Order cannot change inside a reach, because it only changes at a confluence and a reach has
          // none in its interior. So the head cell's order is the reach's order.
          strahler = strahler[start],
          downstream = -1,
          endsInLake = endsInLake,
          endsInSea = endsInSea
        )
      )
    }

    val linked = reaches.map { reach ->
      Reach(
        id = reach.id,
        cells = reach.cells,
        strahler = reach.strahler,
        downstream = reachStartingAt[reach.mouth] ?: -1,
        endsInLake = reach.endsInLake,
        endsInSea = reach.endsInSea
      )
    }

    val confluences = (0 until size).filter { channel[it] && donors[it] >= 2 }.toIntArray()

    return RiverGraph(linked, confluences, strahler, channel)
  }

  /**
   * Strahler order, in one reverse pass over the drainage stack.
   *
   * A source is order 1; a cell fed by one channel keeps its order; a cell where the two largest
   * incoming orders are equal goes up by one. Because the stack lists every cell after the cell it
   * drains into, walking it backwards visits every donor before the cell it feeds, so no per-cell
   * upstream search is needed.
   */
  private fun strahlerOrders(network: DrainageNetwork, channel: BooleanArray): IntArray {
    val order = IntArray(network.size)
    val largest = IntArray(network.size)
    val largestCount = IntArray(network.size)

    for (k in network.stack.indices.reversed()) {
      val i = network.stack[k]
      if (!channel[i]) continue

      order[i] = when {
        largestCount[i] == 0 -> 1
        largestCount[i] >= 2 -> largest[i] + 1
        else -> largest[i]
      }

      val r = network.receiver[i]
      if (r == i || !channel[r]) continue

      when {
        order[i] > largest[r] -> {
          largest[r] = order[i]
          largestCount[r] = 1
        }
        order[i] == largest[r] -> largestCount[r]++
      }
    }

    return order
  }

  private fun channelDonorCounts(network: DrainageNetwork, channel: BooleanArray): IntArray {
    val donors = IntArray(network.size)
    for (i in 0 until network.size) {
      if (!channel[i]) continue
      val r = network.receiver[i]
      if (r != i && channel[r]) donors[r]++
    }
    return donors
  }
}
