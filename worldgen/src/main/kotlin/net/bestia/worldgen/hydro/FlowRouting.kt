package net.bestia.worldgen.hydro

import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.DoubleIntHeap
import net.bestia.worldgen.fields.Grid

/**
 * A solved drainage network over one grid: where every cell sends its water, and an order in which the
 * whole network can be traversed in one pass.
 *
 * Both erosion and hydrology need this, which is why it is a library rather than living inside either
 * of them. They need it at different times - erosion re-solves it every timestep on a surface it is
 * still changing, hydrology solves it once on the final surface to extract the authoritative river
 * network - and a stage that shared the other's answer would be reading a network for a landscape that
 * no longer exists.
 */
class DrainageNetwork(
  val width: Int,
  val height: Int,
  val metresPerCell: Double,

  /** The depression-filled surface. Every cell on it has a path downhill to an outlet. */
  val filled: Grid,

  /**
   * Metres each cell was raised by the fill. Positive means the cell sat in a depression, so this is
   * also the lake bathymetry: fill depth is water depth if the basin holds water to its rim.
   */
  val fillDepth: Grid,

  /** Index of the cell each cell drains into; equal to its own index for an outlet. */
  val receiver: IntArray,

  /** D8 index of the flow direction, or [D8.NONE] for an outlet. */
  val direction: IntArray,

  /** Every cell exactly once, with every cell appearing after the cell it drains into. */
  val stack: IntArray,

  /** Cells that are sea: connected to the edge of the world at or below sea level. */
  val ocean: BooleanArray
) {

  val size get() = width * height

  fun index(x: Int, y: Int) = y * width + x

  fun isOutlet(i: Int) = receiver[i] == i

  /** Distance from cell [i] to the cell it drains into, in metres. Zero for an outlet. */
  fun flowLength(i: Int): Double {
    val d = direction[i]
    return if (d == D8.NONE) 0.0 else D8.LENGTH[d] * metresPerCell
  }

  /**
   * Accumulates [weight] downstream: every cell ends up holding its own weight plus that of everything
   * that drains through it.
   *
   * One reverse pass over [stack], which is what makes this O(n) rather than a per-cell upstream
   * search. The stack has every cell after its receiver, so walking it backwards visits every donor
   * before the cell it donates to.
   */
  fun accumulate(weight: (index: Int) -> Double): Grid {
    val grid = Grid(width, height, DoubleArray(size) { weight(it) })

    for (k in stack.indices.reversed()) {
      val i = stack[k]
      val r = receiver[i]
      if (r != i) grid.data[r] += grid.data[i]
    }

    return grid
  }
}

/**
 * Depression filling, flow direction and flow accumulation.
 *
 * This is where most generators fail, and the failure is always the same: they route flow on a surface
 * that has pits in it, so rivers terminate in the middle of nowhere. Filling first, with Barnes'
 * Priority-Flood, guarantees that every cell drains to the ocean or off the edge of the map - and the
 * epsilon it adds while filling means flow directions are well defined even across a dead-flat lake bed,
 * where the unfilled surface would have no gradient to follow at all.
 */
object FlowRouting {

  /**
   * Millimetre-scale gradient imposed on filled flats.
   *
   * Small enough to be invisible in the terrain, large enough to survive in double precision across a
   * flat thousands of cells wide.
   */
  const val EPSILON = 1e-4

  /** Solve everything: ocean mask, fill, route, order. */
  fun solve(elevation: Grid, seaLevel: Double, metresPerCell: Double): DrainageNetwork {
    val ocean = oceanMask(elevation, seaLevel)
    val (filled, fillDepth) = priorityFlood(elevation, ocean)
    return route(filled, fillDepth, ocean, metresPerCell)
  }

  /**
   * Sea is water connected to the edge of the world, not merely anything below sea level.
   *
   * The distinction is the difference between the Dead Sea and the Mediterranean. An inland basin whose
   * floor is below sea level is not ocean; it is an endorheic lake, it has its own surface level set by
   * evaporation, and treating it as sea would flood a continent through a hole that does not exist.
   */
  fun oceanMask(elevation: Grid, seaLevel: Double): BooleanArray {
    val width = elevation.width
    val height = elevation.height
    val ocean = BooleanArray(width * height)
    val queue = ArrayDeque<Int>()

    fun seed(x: Int, y: Int) {
      val i = y * width + x
      if (!ocean[i] && elevation.data[i] <= seaLevel) {
        ocean[i] = true
        queue.addLast(i)
      }
    }

    for (x in 0 until width) {
      seed(x, 0)
      seed(x, height - 1)
    }
    for (y in 0 until height) {
      seed(0, y)
      seed(width - 1, y)
    }

    while (queue.isNotEmpty()) {
      val i = queue.removeFirst()
      val x = i % width
      val y = i / width
      // Four-connected: a diagonal touch between two below-sea-level cells is not a strait wide enough
      // for the sea to get through, and eight-connectivity here floods basins through single corners.
      for (d in 0 until 8 step 2) {
        val nx = x + D8.DX[d]
        val ny = y + D8.DY[d]
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
        seed(nx, ny)
      }
    }

    return ocean
  }

  /**
   * Barnes' Priority-Flood with epsilon.
   *
   * Push the ocean and the map edge into a priority queue, then repeatedly pop the lowest cell and
   * raise each of its unvisited neighbours to `max(its own elevation, this cell + epsilon)`. Because
   * cells are processed in ascending order of *filled* elevation, the first time a cell is reached is
   * along the lowest path from the outside world to it, which is exactly the water level a basin would
   * reach before spilling.
   *
   * @return the filled surface and, per cell, how far it was raised
   */
  fun priorityFlood(elevation: Grid, ocean: BooleanArray): Pair<Grid, Grid> {
    val width = elevation.width
    val height = elevation.height
    val filled = elevation.copy()
    val closed = BooleanArray(width * height)
    val heap = DoubleIntHeap(width * 4 + height * 4)

    fun open(i: Int) {
      if (closed[i]) return
      closed[i] = true
      heap.push(filled.data[i], i)
    }

    // Two kinds of seed, both of which are places water genuinely leaves the modelled area: the sea,
    // and the edge of the map.
    for (i in ocean.indices) {
      if (ocean[i]) open(i)
    }
    for (x in 0 until width) {
      open(x)
      open((height - 1) * width + x)
    }
    for (y in 0 until height) {
      open(y * width)
      open(y * width + width - 1)
    }

    while (!heap.isEmpty) {
      val i = heap.pop()
      val x = i % width
      val y = i / width
      val here = filled.data[i]

      for (d in 0 until 8) {
        val nx = x + D8.DX[d]
        val ny = y + D8.DY[d]
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue

        val n = ny * width + nx
        if (closed[n]) continue

        closed[n] = true
        val raised = here + EPSILON
        filled.data[n] = if (filled.data[n] > raised) filled.data[n] else raised
        heap.push(filled.data[n], n)
      }
    }

    val depth = Grid(width, height, DoubleArray(filled.data.size) {
      (filled.data[it] - elevation.data[it]).coerceAtLeast(0.0)
    })

    return filled to depth
  }

  /**
   * D8 flow direction plus the traversal order.
   *
   * D8 rather than D-infinity: it is cheap, it is what makes a discrete river *path* to trace into a
   * polyline, and the staircase it produces is removed at vector level by smoothing the centerline -
   * which has to happen anyway, because a river that followed cell edges would look like a canal.
   */
  fun route(
    filled: Grid,
    fillDepth: Grid,
    ocean: BooleanArray,
    metresPerCell: Double
  ): DrainageNetwork {
    val width = filled.width
    val height = filled.height
    val n = width * height

    val receiver = IntArray(n)
    val direction = IntArray(n) { D8.NONE }

    for (y in 0 until height) {
      for (x in 0 until width) {
        val i = y * width + x

        // The sea and the map edge are where water leaves. Everything else must find a way downhill,
        // and after the fill it always can.
        if (ocean[i] || x == 0 || y == 0 || x == width - 1 || y == height - 1) {
          receiver[i] = i
          continue
        }

        val here = filled.data[i]
        var bestDrop = 0.0
        var bestDir = D8.NONE
        var bestReceiver = i

        for (d in 0 until 8) {
          val nx = x + D8.DX[d]
          val ny = y + D8.DY[d]
          val j = ny * width + nx
          // Strictly greater, and the neighbour order is fixed, so ties always resolve the same way.
          val drop = (here - filled.data[j]) / D8.LENGTH[d]
          if (drop > bestDrop) {
            bestDrop = drop
            bestDir = d
            bestReceiver = j
          }
        }

        receiver[i] = bestReceiver
        direction[i] = bestDir
      }
    }

    return DrainageNetwork(
      width = width,
      height = height,
      metresPerCell = metresPerCell,
      filled = filled,
      fillDepth = fillDepth,
      receiver = receiver,
      direction = direction,
      stack = buildStack(receiver),
      ocean = ocean
    )
  }

  /**
   * Braun & Willett's stack: every cell listed after the cell it drains into.
   *
   * Built by walking outwards from the outlets through the donor lists, which is O(n) with no sorting
   * and no recursion. One pass over it solves flow accumulation; one pass over it in the other
   * direction solves the implicit stream power equation for the whole landscape.
   */
  private fun buildStack(receiver: IntArray): IntArray {
    val n = receiver.size

    val donorCount = IntArray(n + 1)
    for (i in 0 until n) {
      if (receiver[i] != i) donorCount[receiver[i] + 1]++
    }
    for (i in 1 until donorCount.size) {
      donorCount[i] += donorCount[i - 1]
    }

    val donorStart = donorCount
    val donors = IntArray(n)
    val cursor = donorStart.copyOf()
    for (i in 0 until n) {
      if (receiver[i] != i) donors[cursor[receiver[i]]++] = i
    }

    val stack = IntArray(n)
    var top = 0
    for (i in 0 until n) {
      if (receiver[i] == i) stack[top++] = i
    }

    var read = 0
    while (read < top) {
      val c = stack[read++]
      for (slot in donorStart[c] until donorStart[c + 1]) {
        stack[top++] = donors[slot]
      }
    }

    check(top == n) {
      // Only reachable if the fill left a cycle, which would mean Priority-Flood was wrong. Failing
      // loudly here beats an erosion run that silently ignores a chunk of the map.
      "Drainage stack reached $top of $n cells; the filled surface still contains a closed loop"
    }

    return stack
  }
}
