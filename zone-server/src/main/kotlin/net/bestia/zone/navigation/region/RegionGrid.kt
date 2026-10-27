package net.bestia.zone.navigation.region

import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service
import java.util.BitSet
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The world in [CELL] metre squares, each either dry enough to walk or water: the tier between the macro graph,
 * whose nodes are kilometres apart and blind to a lake between two of them, and the local search, which sees
 * every voxel but no further than a chunk ahead.
 *
 * What it is for is the stretches the graph does not cover - from where a traveller stands to the graph, from
 * the graph to the click, and the whole way when the graph has nothing to offer. Those were straight lines, and
 * a straight line across a bay walked the traveller to the shore and left it there.
 *
 * Built [BLOCK] cells at a time, the first time a way is asked through them, and kept. A block is a bit per
 * cell, so even [MAX_BLOCKS] of them are a few hundred kilobytes.
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

    /** The search reached ground not surveyed yet and the budget is spent. Ask again next tick. */
    data object Pending : Way
  }

  private val blocks = object : LinkedHashMap<Long, BitSet>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, BitSet>) = size > MAX_BLOCKS
  }

  /** Whether [position] stands in water too deep to wade, sampled exactly rather than by cell. */
  fun isWaterAt(position: Vec3L): Boolean {
    if (!water.isReady) return false

    val x = position.x + 0.5
    val y = position.y + 0.5
    return water.survey(Aabb(x - 1.0, y - 1.0, x + 1.0, y + 1.0), exact = true).isDeep(x, y)
  }

  /**
   * A way over dry cells from [from] to [to].
   *
   * Searched within a box round the two ends, half as wide again as they are apart and never under
   * [MIN_MARGIN_CELLS]: a lake in between is walked round, and a destination on an island is refused rather
   * than searched for across the whole world. The two ends' own cells always count as dry, so a traveller
   * standing on a jetty or a click on the beach is not lost to a cell that is mostly sea.
   *
   * A block costs some ten milliseconds to survey and an island's whole box is a few dozen of them, so each one
   * not yet known is paid for out of [budget]; once that is spent the answer is [Way.Pending], and the next
   * call carries on where this one stopped, with everything it surveyed kept.
   */
  fun wayBetween(from: Vec3L, to: Vec3L, budget: SurveyBudget = SurveyBudget.UNLIMITED): Way {
    if (!water.isReady) return Way.Unknown

    val start = Cell(cellOf(from.x), cellOf(from.y))
    val goal = Cell(cellOf(to.x), cellOf(to.y))
    if (start == goal) return Way.Found(listOf(to))

    val margin = max(MIN_MARGIN_CELLS, max(abs(goal.x - start.x), abs(goal.y - start.y)) / 2)
    val box = Box(
      min(start.x, goal.x) - margin, min(start.y, goal.y) - margin,
      max(start.x, goal.x) + margin, max(start.y, goal.y) + margin,
    )
    val search = Search(start, goal, box, budget)
    val cells = search.run()
    // Unsurveyed ground counted as water, so whatever came of it is only an answer once nothing was.
    if (search.stalled) return Way.Pending
    if (cells == null) return Way.None
    if (cells.isEmpty()) return Way.Unknown

    val points = corners(cells, search::isOpen).drop(1).map { it.centre() }.toMutableList()
    points[points.lastIndex] = to
    return Way.Found(points)
  }

  /** Cells joined in a straight line where nothing wet lies between, so a way is a few corners, not a staircase. */
  private fun corners(cells: List<Cell>, isOpen: (Cell) -> Boolean): List<Cell> {
    val kept = arrayListOf(cells.first())
    var anchor = cells.first()

    for (i in 1 until cells.lastIndex) {
      if (!isClearLine(anchor, cells[i + 1], isOpen)) {
        anchor = cells[i]
        kept.add(anchor)
      }
    }

    kept.add(cells.last())
    return kept
  }

  /** Sampled a quarter cell at a time, which no line can slip a wet cell's corner between. */
  private fun isClearLine(a: Cell, b: Cell, isOpen: (Cell) -> Boolean): Boolean {
    val dx = (b.x - a.x).toDouble()
    val dy = (b.y - a.y).toDouble()
    val steps = ceil(hypot(dx, dy) * 4).toInt()

    for (i in 1 until steps) {
      val t = i.toDouble() / steps
      if (!isOpen(cellAt(a.x + 0.5 + dx * t, a.y + 0.5 + dy * t))) return false
    }

    return true
  }

  private fun cellAt(x: Double, y: Double) = Cell(Math.floor(x).toInt(), Math.floor(y).toInt())

  /** Null when the cell's block is not surveyed yet and [budget] allows no more. */
  private fun isWet(cell: Cell, budget: SurveyBudget): Boolean? {
    val bx = Math.floorDiv(cell.x, BLOCK)
    val by = Math.floorDiv(cell.y, BLOCK)
    val block = blockAt(bx, by, budget) ?: return null
    return block[(cell.y - by * BLOCK) * BLOCK + (cell.x - bx * BLOCK)]
  }

  @Synchronized
  private fun blockAt(bx: Int, by: Int, budget: SurveyBudget): BitSet? {
    val key = (bx.toLong() shl 32) or (by.toLong() and 0xFFFFFFFFL)
    blocks[key]?.let { return it }
    if (!budget.trySpend()) return null

    return survey(bx, by).also { blocks[key] = it }
  }

  /**
   * A cell is wet when most of its [SAMPLES] by [SAMPLES] samples are: a stream narrower than a cell leaves it
   * dry, which is right, because the local tier fords or steps over it; a river wider than one does not.
   */
  private fun survey(bx: Int, by: Int): BitSet {
    val minX = bx.toDouble() * BLOCK * CELL
    val minY = by.toDouble() * BLOCK * CELL
    val size = BLOCK.toDouble() * CELL
    val survey = water.survey(Aabb(minX, minY, minX + size, minY + size), exact = false)
    val wet = BitSet(BLOCK * BLOCK)

    for (cy in 0 until BLOCK) {
      for (cx in 0 until BLOCK) {
        var deep = 0
        for (sy in 0 until SAMPLES) {
          for (sx in 0 until SAMPLES) {
            val x = minX + (cx + (sx + 0.5) / SAMPLES) * CELL
            val y = minY + (cy + (sy + 0.5) / SAMPLES) * CELL
            if (survey.isDeep(x, y)) deep++
          }
        }
        if (deep * 2 > SAMPLES * SAMPLES) wet.set(cy * BLOCK + cx)
      }
    }

    // A bridge is the way over, however much water lies under it.
    for ((a, b) in survey.crossings) {
      val steps = ceil(hypot(b.x - a.x, b.y - a.y) / CROSSING_STEP_METRES).toInt().coerceAtLeast(1)
      for (i in 0..steps) {
        val t = i.toDouble() / steps
        val cx = Math.floor((a.x + (b.x - a.x) * t - minX) / CELL).toInt()
        val cy = Math.floor((a.y + (b.y - a.y) * t - minY) / CELL).toInt()
        if (cx in 0 until BLOCK && cy in 0 until BLOCK) wet.clear(cy * BLOCK + cx)
      }
    }

    return wet
  }

  private data class Cell(val x: Int, val y: Int) {
    fun centre() = Vec3L(x.toLong() * CELL + CELL / 2, y.toLong() * CELL + CELL / 2, 0L)
  }

  private class Box(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    operator fun contains(cell: Cell) = cell.x in minX..maxX && cell.y in minY..maxY
  }

  /**
   * A* over the cells in one box, eight ways round and never cutting a wet corner.
   *
   * Its own rather than [net.bestia.zone.navigation.graph.AStar], which answers null both when the box holds no
   * way and when it ran out of expansions - and those are a refusal and a shrug respectively.
   */
  private inner class Search(
    private val start: Cell,
    private val goal: Cell,
    private val box: Box,
    private val budget: SurveyBudget,
  ) {

    /** Whether a cell was taken for water only because its block could not be surveyed. */
    var stalled = false
      private set

    fun isOpen(cell: Cell): Boolean {
      if (cell == start || cell == goal) return true
      if (cell !in box) return false

      val wet = isWet(cell, budget) ?: run { stalled = true; true }
      return !wet
    }

    /** The cells from start to goal; null when there is none in the box, empty when the search gave up. */
    fun run(): List<Cell>? {
      val cameFrom = HashMap<Cell, Cell>()
      val cost = HashMap<Cell, Double>()
      val closed = HashSet<Cell>()
      val open = PriorityQueue<Pair<Cell, Double>>(compareBy { it.second })

      cost[start] = 0.0
      open.add(start to estimate(start))

      while (open.isNotEmpty()) {
        val current = open.poll().first
        if (current == goal) return walkBack(cameFrom)
        if (!closed.add(current)) continue
        if (closed.size > MAX_EXPANSIONS) return emptyList()

        val here = cost.getValue(current)
        for ((dx, dy) in STEPS) {
          val next = Cell(current.x + dx, current.y + dy)
          if (next in closed || !isOpen(next)) continue
          // Diagonally only between two open cells: slipping between two wet ones is wading through a corner.
          if (dx != 0 && dy != 0 && !(isOpen(Cell(current.x + dx, current.y)) && isOpen(Cell(current.x, current.y + dy)))) {
            continue
          }

          val tentative = here + if (dx != 0 && dy != 0) DIAGONAL else 1.0
          if (tentative >= (cost[next] ?: Double.MAX_VALUE)) continue

          cost[next] = tentative
          cameFrom[next] = current
          open.add(next to tentative + estimate(next))
        }
      }

      return null
    }

    /** Octile distance, which eight-way steps can never beat. */
    private fun estimate(cell: Cell): Double {
      val dx = abs(goal.x - cell.x)
      val dy = abs(goal.y - cell.y)
      return max(dx, dy) + (DIAGONAL - 1.0) * min(dx, dy)
    }

    private fun walkBack(cameFrom: Map<Cell, Cell>): List<Cell> {
      val path = arrayListOf(goal)
      var at = goal
      while (at != start) {
        at = cameFrom.getValue(at)
        path.add(at)
      }
      return path.asReversed()
    }
  }

  private companion object {
    /** One chunk, so a cell is the area the local tier searches in one go. */
    const val CELL = 32L

    /** Cells along a block's side: a kilometre, roughly. */
    const val BLOCK = 32

    const val MAX_BLOCKS = 8_192

    /** Samples along a cell's side. Odd, so a cell is never tied between wet and dry. */
    const val SAMPLES = 3

    /** Two kilometres either side of a short way: room to walk round a lake, not to search a coast. */
    const val MIN_MARGIN_CELLS = 64

    const val MAX_EXPANSIONS = 200_000

    /** Finer than any deck is wide, so no cell a deck crosses is missed. */
    const val CROSSING_STEP_METRES = 4.0

    val DIAGONAL = Math.sqrt(2.0)

    val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)

    fun cellOf(position: Long) = Math.floorDiv(position, CELL).toInt()
  }
}
