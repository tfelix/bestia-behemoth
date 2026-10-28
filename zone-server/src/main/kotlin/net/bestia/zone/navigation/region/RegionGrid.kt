package net.bestia.zone.navigation.region

import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The world in [CELL] metre squares, each with the height of its ground, whether it is water or too steep to
 * walk, and whether a road runs through it: the tier between the macro graph, whose nodes are kilometres apart
 * and blind to a lake or a ridge between two of them, and the local search, which sees every voxel but no
 * further than a chunk ahead.
 *
 * It is where a journey's way is laid. A way over it keeps to the valleys and the roads, because a step costs
 * what walking it costs: uphill dearer than on the flat, downhill no cheaper, a road a little cheaper than open
 * country. The macro graph only says which way to head when the destination is further than one search spans.
 *
 * Built [BLOCK] cells at a time, the first time a way is asked through them, and kept. A block is two bytes of
 * height per cell and two bits of flags, so even [MAX_BLOCKS] of them are under ten megabytes.
 */
@Service
class RegionGrid(private val water: WaterSource) {

  /** What [wayBetween] found. */
  sealed interface Way {

    /** The corners to walk by, the last one the destination itself. */
    class Found(val points: List<Vec3L>) : Way

    /** Water all round, near enough to search: there is no way there on foot. */
    data object None : Way

    /** The grid cannot tell - no world yet, or a way so long the search gave up. Walk straight. */
    data object Unknown : Way

    /** Still being searched for; see [WaySearches]. Ask again next tick. */
    data object Pending : Way
  }

  /**
   * One block of cells.
   *
   * @property heights the mean height of each cell's ground, in [HEIGHT_UNIT]s
   * @property closed water too deep to wade, or ground too steep to climb
   * @property roads a road or a street runs through, or a bridge deck spans
   */
  private class Block(val heights: ShortArray, val closed: BitSet, val roads: BitSet)

  private val blocks = object : LinkedHashMap<Long, Block>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Block>) = size > MAX_BLOCKS
  }

  /** Whether [position] stands in water too deep to wade, sampled exactly rather than by cell. */
  fun isWaterAt(position: Vec3L): Boolean {
    if (!water.isReady) return false

    val x = position.x + 0.5
    val y = position.y + 0.5
    return water.survey(Aabb(x - 1.0, y - 1.0, x + 1.0, y + 1.0), exact = true).isDeep(x, y)
  }

  /**
   * The cheapest way over open cells from [from] to [to].
   *
   * Searched within a box round the two ends, half as wide again as they are apart and never under
   * [MIN_MARGIN_CELLS]: a lake in between is walked round, and a destination on an island is refused rather
   * than searched for across the whole world. The two ends' own cells always count as open, so a traveller
   * standing on a jetty or a click on the beach is not lost to a cell that is mostly sea.
   *
   * A block costs ten to thirty milliseconds to survey and a long way's search reaches dozens of them, and the
   * search itself may take tens of milliseconds more: far too long for a tick. The game asks through
   * [WaySearches], which runs this off the tick thread. Safe to call from any thread.
   *
   * @param avoid stretches, such as a bridge the traveller believes has fallen, that the way must keep
   *   [AVOID_REACH] metres clear of
   */
  fun wayBetween(from: Vec3L, to: Vec3L, avoid: List<Pair<Vec3L, Vec3L>> = emptyList()): Way {
    if (!water.isReady) return Way.Unknown

    val start = keyOf(cellOf(from.x), cellOf(from.y))
    val goal = keyOf(cellOf(to.x), cellOf(to.y))
    if (start == goal) return Way.Found(listOf(to))

    val margin = max(MIN_MARGIN_CELLS, max(abs(xOf(goal) - xOf(start)), abs(yOf(goal) - yOf(start))) / 2)
    val box = Box(
      min(xOf(start), xOf(goal)) - margin, min(yOf(start), yOf(goal)) - margin,
      max(xOf(start), xOf(goal)) + margin, max(yOf(start), yOf(goal)) + margin,
    )
    val avoided = avoided(avoid)
    var search = Search(start, goal, box, avoided, HEURISTIC_WEIGHT)
    var cells = search.run()
    if (cells?.isEmpty() == true) {
      // Round a long lake or along a coast the close search runs out of cells; a greedy one finds a way.
      search = Search(start, goal, box, avoided, GREEDY_HEURISTIC_WEIGHT)
      cells = search.run()
    }
    if (cells == null) return Way.None
    if (cells.isEmpty()) return Way.Unknown

    val points = corners(cells, search).drop(1).map { centreOf(it) }.toMutableList()
    points[points.lastIndex] = to
    return Way.Found(points)
  }

  /**
   * The cells of a way joined in straight lines wherever a line is open and costs little more than the cells it
   * replaces, so a way is a few corners, not a staircase - but a way round a hill is not straightened back over
   * it, and one along a winding road keeps the road's bends.
   */
  private fun corners(cells: List<Long>, search: Search): List<Long> {
    val costSoFar = DoubleArray(cells.size)
    for (i in 1 until cells.size) costSoFar[i] = costSoFar[i - 1] + search.stepCost(cells[i - 1], cells[i])

    val kept = arrayListOf(cells.first())
    var anchor = 0

    for (i in 1 until cells.lastIndex) {
      val alongCells = costSoFar[i + 1] - costSoFar[anchor]
      if (!search.isWorthTakingStraight(cells[anchor], cells[i + 1], alongCells)) {
        anchor = i
        kept.add(cells[anchor])
      }
    }

    kept.add(cells.last())
    return kept
  }

  /** The cells within [AVOID_REACH] of any of [stretches]. */
  private fun avoided(stretches: List<Pair<Vec3L, Vec3L>>): Set<Long> {
    if (stretches.isEmpty()) return emptySet()

    val cells = HashSet<Long>()
    for ((a, b) in stretches) {
      val steps = ceil(hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) / AVOID_STEP_METRES).toInt().coerceAtLeast(1)
      for (i in 0..steps) {
        val t = i.toDouble() / steps
        val x = a.x + (b.x - a.x) * t
        val y = a.y + (b.y - a.y) * t
        val reach = ceil(AVOID_REACH / CELL).toInt()
        for (dy in -reach..reach) {
          for (dx in -reach..reach) {
            val cx = Math.floorDiv(x.toLong(), CELL).toInt() + dx
            val cy = Math.floorDiv(y.toLong(), CELL).toInt() + dy
            val centre = centreOf(keyOf(cx, cy))
            if (hypot(centre.x - x, centre.y - y) <= AVOID_REACH) cells.add(keyOf(cx, cy))
          }
        }
      }
    }
    return cells
  }

  /**
   * The block, surveyed now if it is not yet and [surveying]; otherwise null. Surveyed outside the lock, so a
   * survey on one thread does not hold up a look-up on another; two threads may then survey the same block.
   */
  private fun blockAt(bx: Int, by: Int, surveying: Boolean): Block? {
    val key = keyOf(bx, by)
    synchronized(blocks) { blocks[key] }?.let { return it }
    if (!surveying) return null

    val block = survey(bx, by)
    synchronized(blocks) { blocks[key] = block }
    return block
  }

  /**
   * A cell is water when most of its [SAMPLES] by [SAMPLES] samples are: a stream narrower than a cell leaves
   * it open, which is right, because the local tier fords or steps over it; a river wider than one does not.
   *
   * It is too steep when the plane through its samples rises more than [MAX_GRADE]: a cliff a whole cell high,
   * not a bank or a boulder the local tier walks round.
   */
  private fun survey(bx: Int, by: Int): Block {
    val minX = bx.toDouble() * BLOCK * CELL
    val minY = by.toDouble() * BLOCK * CELL
    val size = BLOCK.toDouble() * CELL
    val survey = water.survey(Aabb(minX, minY, minX + size, minY + size), exact = false)
    val block = Block(ShortArray(BLOCK * BLOCK), BitSet(BLOCK * BLOCK), BitSet(BLOCK * BLOCK))
    val ground = DoubleArray(SAMPLES * SAMPLES)
    val spacing = CELL.toDouble() / SAMPLES

    for (cy in 0 until BLOCK) {
      for (cx in 0 until BLOCK) {
        var deep = 0
        for (sy in 0 until SAMPLES) {
          for (sx in 0 until SAMPLES) {
            val x = minX + (cx + (sx + 0.5) / SAMPLES) * CELL
            val y = minY + (cy + (sy + 0.5) / SAMPLES) * CELL
            ground[sy * SAMPLES + sx] = survey.groundAt(x, y)
            if (survey.isDeep(x, y)) deep++
          }
        }

        val index = cy * BLOCK + cx
        block.heights[index] = heightOf(ground.average())
        val isSteep = gradeOf(ground, spacing) > MAX_GRADE
        if (deep * 2 > SAMPLES * SAMPLES || isSteep) block.closed.set(index)
      }
    }

    for (road in survey.roads) {
      for ((a, b) in road.zipWithNext()) {
        forEachCellAlong(a, b, minX, minY) { index, _ -> block.roads.set(index) }
      }
    }

    // A bridge is the way over, however much water lies under it, and at the height of its deck.
    for ((a, b) in survey.crossings) {
      val fromBank = survey.groundAt(a.x, a.y)
      val toBank = survey.groundAt(b.x, b.y)
      forEachCellAlong(a, b, minX, minY) { index, t ->
        block.closed.clear(index)
        block.roads.set(index)
        block.heights[index] = heightOf(fromBank + (toBank - fromBank) * t)
      }
    }

    return block
  }

  /** Every cell of the block at [minX], [minY] a line from [a] to [b] passes through, and how far along. */
  private inline fun forEachCellAlong(a: Vec2d, b: Vec2d, minX: Double, minY: Double, action: (Int, Double) -> Unit) {
    val steps = ceil(hypot(b.x - a.x, b.y - a.y) / LINE_STEP_METRES).toInt().coerceAtLeast(1)
    for (i in 0..steps) {
      val t = i.toDouble() / steps
      val cx = floor((a.x + (b.x - a.x) * t - minX) / CELL).toInt()
      val cy = floor((a.y + (b.y - a.y) * t - minY) / CELL).toInt()
      if (cx in 0 until BLOCK && cy in 0 until BLOCK) action(cy * BLOCK + cx, t)
    }
  }

  private class Box(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    operator fun contains(cell: Long) = xOf(cell) in minX..maxX && yOf(cell) in minY..maxY
  }

  /**
   * A* over the cells in one box, eight ways round and never cutting a closed corner.
   *
   * Its own rather than [net.bestia.zone.navigation.graph.AStar], which answers null both when the box holds no
   * way and when it ran out of expansions - and those are a refusal and a shrug respectively. Cells are packed
   * into longs and its nodes kept in [Nodes], so a search allocates arrays, not a box per cell.
   */
  private inner class Search(
    private val start: Long,
    private val goal: Long,
    private val box: Box,
    private val avoided: Set<Long>,
    private val weight: Double,
  ) {

    /** The cells from start to goal; null when there is none in the box, empty when the search gave up. */
    fun run(): List<Long>? {
      val nodes = Nodes()
      val open = Heap()
      open.push(nodes.add(start, 0.0, parent = -1), estimate(start))

      var expanded = 0
      while (open.isNotEmpty) {
        val node = open.pop()
        if (nodes.isClosed(node)) continue
        val current = nodes.cell(node)
        if (current == goal) return nodes.walkBack(node)
        nodes.close(node)
        if (++expanded > MAX_EXPANSIONS) return emptyList()

        val here = nodes.cost(node)
        val x = xOf(current)
        val y = yOf(current)
        for (direction in STEPS_X.indices) {
          val dx = STEPS_X[direction]
          val dy = STEPS_Y[direction]
          val next = keyOf(x + dx, y + dy)
          val known = nodes.find(next)
          if (known >= 0 && nodes.isClosed(known)) continue
          if (!isOpen(next)) continue
          // Diagonally only between two open cells: slipping between two closed ones is cutting a corner.
          if (dx != 0 && dy != 0 && !(isOpen(keyOf(x + dx, y)) && isOpen(keyOf(x, y + dy)))) continue

          val step = stepCost(current, next)
          if (step.isInfinite()) continue
          val tentative = here + step

          if (known < 0) {
            open.push(nodes.add(next, tentative, parent = node), tentative + estimate(next))
          } else if (tentative < nodes.cost(known)) {
            nodes.improve(known, tentative, parent = node)
            open.push(known, tentative + estimate(next))
          }
        }
      }

      return null
    }

    /** The last block looked up, since a search asks about the same one many times running. */
    private var lastBlockKey = Long.MIN_VALUE
    private var lastBlock: Block? = null

    /** The block [cell] lies in, surveyed now if it is not yet. */
    private fun blockOf(cell: Long): Block {
      return surveyedBlockOf(cell, surveying = true)!!
    }

    /** The block [cell] lies in if it is surveyed, or is surveyed now when [surveying]; null otherwise. */
    private fun surveyedBlockOf(cell: Long, surveying: Boolean): Block? {
      val bx = Math.floorDiv(xOf(cell), BLOCK)
      val by = Math.floorDiv(yOf(cell), BLOCK)
      val key = keyOf(bx, by)
      if (key == lastBlockKey) return lastBlock

      val block = blockAt(bx, by, surveying) ?: return null
      lastBlockKey = key
      lastBlock = block
      return block
    }

    /** @param surveying whether a cell in a block not surveyed yet may be surveyed; if not, it is not open */
    fun isOpen(cell: Long, surveying: Boolean = true): Boolean {
      if (cell == start || cell == goal) return true
      if (cell !in box || cell in avoided) return false

      val block = surveyedBlockOf(cell, surveying) ?: return false
      return !block.closed[indexOf(cell)]
    }

    /**
     * What stepping from [from] to the neighbouring [to] costs, in cells walked on the flat: dearer uphill,
     * no cheaper downhill, a little cheaper on a road. Infinite up or down a cliff.
     */
    fun stepCost(from: Long, to: Long): Double {
      val fromBlock = blockOf(from)
      val fromHeight = metresOf(fromBlock.heights[indexOf(from)])
      val fromSurface = surfaceOf(fromBlock, indexOf(from))
      val toBlock = blockOf(to)
      val toHeight = metresOf(toBlock.heights[indexOf(to)])
      val toSurface = surfaceOf(toBlock, indexOf(to))
      val length = if (xOf(from) != xOf(to) && yOf(from) != yOf(to)) DIAGONAL else 1.0

      return length * (fromSurface + toSurface) / 2.0 * climbOf((toHeight - fromHeight) / (length * CELL))
    }

    /**
     * Whether a straight line from cell [a] to cell [b] is open all along and costs at most [LINE_SLACK] more
     * than [alongCells], what the cells between them cost. Sampled a quarter cell at a time, which no line can
     * slip a closed cell's corner between, over ground whose height is blended between cell middles.
     */
    fun isWorthTakingStraight(a: Long, b: Long, alongCells: Double): Boolean {
      val ax = xOf(a) + 0.5
      val ay = yOf(a) + 0.5
      val dx = xOf(b) + 0.5 - ax
      val dy = yOf(b) + 0.5 - ay
      val steps = ceil(hypot(dx, dy) * 4).toInt()
      val stepLength = hypot(dx, dy) / steps
      val limit = alongCells * (1.0 + LINE_SLACK)

      var cost = 0.0
      var height = heightAt(ax, ay)
      for (i in 1..steps) {
        val t = i.toDouble() / steps
        val x = ax + dx * t
        val y = ay + dy * t
        val cell = keyOf(floor(x).toInt(), floor(y).toInt())
        if (!isOpen(cell, surveying = false)) return false

        val next = heightAt(x, y)
        // The ends' own cells are open even unsurveyed; a line through one is simply not taken.
        val block = surveyedBlockOf(cell, surveying = false) ?: return false
        cost += stepLength * surfaceOf(block, indexOf(cell)) * climbOf((next - height) / (stepLength * CELL))
        height = next
        if (cost > limit) return false
      }

      return true
    }

    /** The ground's height in metres at a point given in cells, blended between the four nearest middles. */
    private fun heightAt(x: Double, y: Double): Double {
      val left = floor(x - 0.5).toInt()
      val bottom = floor(y - 0.5).toInt()
      val fx = x - 0.5 - left
      val fy = y - 0.5 - bottom
      val own = heightOf(keyOf(floor(x).toInt(), floor(y).toInt())) ?: return 0.0

      fun h(cx: Int, cy: Int) = heightOf(keyOf(cx, cy)) ?: own
      val lower = h(left, bottom) * (1 - fx) + h(left + 1, bottom) * fx
      val upper = h(left, bottom + 1) * (1 - fx) + h(left + 1, bottom + 1) * fx
      return lower * (1 - fy) + upper * fy
    }

    /** A surveyed cell's height, without surveying anything for it. */
    private fun heightOf(cell: Long): Double? {
      val block = surveyedBlockOf(cell, surveying = false) ?: return null
      return metresOf(block.heights[indexOf(cell)])
    }

    /**
     * The octile distance at a road's price, weighted by [weight].
     *
     * At a road's price alone it never over-estimates, but it is blind to every hill and so far under what most
     * steps cost that the search floods the box before it trusts a way. Weighted a little, the search keeps to a
     * corridor and the way it finds costs at most that much more than the cheapest. Weighted much, it walks
     * straight past a road that would have been the better way, since it never looks aside long enough to try it.
     */
    private fun estimate(cell: Long): Double {
      val dx = abs(xOf(goal) - xOf(cell))
      val dy = abs(yOf(goal) - yOf(cell))
      return (max(dx, dy) + (DIAGONAL - 1.0) * min(dx, dy)) * ROAD_SURFACE * weight
    }
  }

  /**
   * The cells a [Search] has reached, in parallel arrays, and an open-addressed table from cell to node: a
   * search of a hundred thousand cells allocates a few arrays rather than a few hundred thousand boxes.
   */
  private class Nodes {
    private var cells = LongArray(INITIAL_NODES)
    private var costs = DoubleArray(INITIAL_NODES)
    private var parents = IntArray(INITIAL_NODES)
    private val closed = BitSet(INITIAL_NODES)
    private var size = 0

    private var slots = IntArray(INITIAL_NODES * 2) { -1 }

    fun cell(node: Int) = cells[node]
    fun cost(node: Int) = costs[node]
    fun isClosed(node: Int) = closed[node]
    fun close(node: Int) = closed.set(node)

    /** The node at [cell], or -1 if the search has not reached it. */
    fun find(cell: Long): Int {
      var slot = slotOf(cell, slots.size)
      while (true) {
        val node = slots[slot]
        if (node < 0 || cells[node] == cell) return node
        slot = (slot + 1) and (slots.size - 1)
      }
    }

    fun add(cell: Long, cost: Double, parent: Int): Int {
      if (size == cells.size) grow()
      val node = size++
      cells[node] = cell
      costs[node] = cost
      parents[node] = parent
      insert(node, slots)
      return node
    }

    fun improve(node: Int, cost: Double, parent: Int) {
      costs[node] = cost
      parents[node] = parent
    }

    fun walkBack(node: Int): List<Long> {
      val path = ArrayList<Long>()
      var at = node
      while (at >= 0) {
        path.add(cells[at])
        at = parents[at]
      }
      return path.asReversed()
    }

    private fun insert(node: Int, table: IntArray) {
      var slot = slotOf(cells[node], table.size)
      while (table[slot] >= 0) slot = (slot + 1) and (table.size - 1)
      table[slot] = node
    }

    private fun grow() {
      val capacity = cells.size * 2
      cells = cells.copyOf(capacity)
      costs = costs.copyOf(capacity)
      parents = parents.copyOf(capacity)
      slots = IntArray(capacity * 2) { -1 }
      for (node in 0 until size) insert(node, slots)
    }

    private fun slotOf(cell: Long, tableSize: Int): Int {
      val mixed = cell * -0x61c8864680b583ebL
      return (mixed ushr 32).toInt() and (tableSize - 1)
    }
  }

  /** A binary min-heap of nodes by their estimated total, in two arrays. A node may sit in it more than once. */
  private class Heap {
    private var nodes = IntArray(INITIAL_NODES)
    private var keys = DoubleArray(INITIAL_NODES)
    private var size = 0

    val isNotEmpty get() = size > 0

    fun push(node: Int, key: Double) {
      if (size == nodes.size) {
        nodes = nodes.copyOf(size * 2)
        keys = keys.copyOf(size * 2)
      }
      var at = size++
      while (at > 0) {
        val parent = (at - 1) / 2
        if (keys[parent] <= key) break
        nodes[at] = nodes[parent]
        keys[at] = keys[parent]
        at = parent
      }
      nodes[at] = node
      keys[at] = key
    }

    fun pop(): Int {
      val top = nodes[0]
      val node = nodes[--size]
      val key = keys[size]
      var at = 0
      while (true) {
        var child = at * 2 + 1
        if (child >= size) break
        if (child + 1 < size && keys[child + 1] < keys[child]) child++
        if (keys[child] >= key) break
        nodes[at] = nodes[child]
        keys[at] = keys[child]
        at = child
      }
      nodes[at] = node
      keys[at] = key
      return top
    }
  }

  private companion object {
    /** One chunk, so a cell is the area the local tier searches in one go. */
    const val CELL = 32L

    /** Cells along a block's side: a kilometre, roughly. */
    const val BLOCK = 32

    const val MAX_BLOCKS = 4_096

    /** Samples along a cell's side. Odd, so a cell is never tied between wet and dry. */
    const val SAMPLES = 3

    /** Two kilometres either side of a short way: room to walk round a lake, not to search a coast. */
    const val MIN_MARGIN_CELLS = 64

    const val MAX_EXPANSIONS = 200_000

    /** Finer than any deck or road is wide, so no cell one crosses is missed. */
    const val LINE_STEP_METRES = 4.0

    const val AVOID_STEP_METRES = 8.0

    /** A way crosses a river only through the cells a deck opens, whose middles lie within 23 m of it. */
    const val AVOID_REACH = 32.0

    /** Heights are kept in half metres, which a short holds to sixteen kilometres either side of the sea. */
    const val HEIGHT_UNIT = 0.5

    /** The steepest a cell's ground may rise, as rise over run, and the steepest step between two cells. */
    const val MAX_GRADE = 1.0

    /** How far [Search]'s estimate over-states the way left. See there for why. */
    const val HEURISTIC_WEIGHT = 1.1

    /** The weight a search that ran out of cells is tried again with: twice as long a way at worst, rarely. */
    const val GREEDY_HEURISTIC_WEIGHT = 2.0

    const val INITIAL_NODES = 4_096

    /** A road is graded and firm; a way goes a little out of its way to walk one. */
    const val ROAD_SURFACE = 0.85

    /**
     * How sharply climbing costs, after Tobler's hiking function: walking pace falls by e^(3.5 |grade + 0.05|).
     * A ten percent climb costs half as much again as the flat, a thirty percent one nearly three times as much.
     */
    const val TOBLER = 3.5

    /** The gentle descent Tobler's function walks fastest. */
    const val EASIEST_GRADE = -0.05

    /** How much dearer than the cells it replaces a straight line may be and still be taken. */
    const val LINE_SLACK = 0.05

    val DIAGONAL = Math.sqrt(2.0)

    val STEPS_X = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    val STEPS_Y = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    fun cellOf(position: Long) = Math.floorDiv(position, CELL).toInt()

    fun keyOf(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

    fun xOf(key: Long) = (key shr 32).toInt()

    fun yOf(key: Long) = key.toInt()

    /** Where in its block a cell lies. */
    fun indexOf(cell: Long) = Math.floorMod(yOf(cell), BLOCK) * BLOCK + Math.floorMod(xOf(cell), BLOCK)

    fun centreOf(cell: Long) = Vec3L(xOf(cell).toLong() * CELL + CELL / 2, yOf(cell).toLong() * CELL + CELL / 2, 0L)

    fun heightOf(metres: Double): Short {
      return (metres / HEIGHT_UNIT).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    fun metresOf(height: Short) = height * HEIGHT_UNIT

    fun surfaceOf(block: Block, index: Int) = if (block.roads[index]) ROAD_SURFACE else 1.0

    /**
     * The cost of walking a grade, as a multiple of the flat: never below one, since a way that ran downhill for
     * the bargain would be a stranger one, and infinite past [MAX_GRADE].
     */
    fun climbOf(grade: Double): Double {
      if (abs(grade) > MAX_GRADE) return Double.POSITIVE_INFINITY
      return max(1.0, exp(TOBLER * (abs(grade - EASIEST_GRADE) - abs(EASIEST_GRADE))))
    }

    /** The rise over run of the plane through [SAMPLES] by [SAMPLES] heights, [spacing] metres apart. */
    fun gradeOf(ground: DoubleArray, spacing: Double): Double {
      val last = SAMPLES - 1
      var east = 0.0
      var north = 0.0
      for (i in 0 until SAMPLES) {
        east += ground[i * SAMPLES + last] - ground[i * SAMPLES]
        north += ground[last * SAMPLES + i] - ground[i]
      }
      val run = spacing * last * SAMPLES
      return hypot(east / run, north / run)
    }
  }
}
