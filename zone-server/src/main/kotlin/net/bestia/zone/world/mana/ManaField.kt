package net.bestia.zone.world.mana

import org.springframework.stereotype.Component
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * The mana play has added to the world or taken from it, on top of the start density.
 *
 * Only the difference is kept, on 128 m cells grouped into 4 km tiles. A tile exists only where something
 * happened, so an untouched world holds no tiles at all.
 *
 * Guarded by its own monitor. Casts, kills, fire and decay release mana from systems that run in parallel within
 * one tick wave, so every method locks the field, and a caller that walks the tiles holds the lock throughout.
 */
@Component
class ManaField {

  private val tiles = HashMap<Long, DoubleArray>()

  /** Tiles changed since the last [takeChanges], and tiles dropped since then. */
  private val changed = HashSet<Long>()
  private val dropped = HashSet<Long>()

  val tileCount: Int
    @Synchronized get() = tiles.size

  @Synchronized
  fun deltaAt(x: Double, y: Double): Double = deltaOf(cellOf(x), cellOf(y))

  @Synchronized
  fun deltaOf(cellX: Int, cellY: Int): Double {
    val tile = tiles[tileKeyOf(cellX, cellY)] ?: return 0.0
    return tile[indexOf(cellX, cellY)]
  }

  /** Spreads [motes] evenly over the cells whose centre lies within [radius] metres, at least the one under it. */
  @Synchronized
  fun add(x: Double, y: Double, motes: Double, radius: Double = 0.0) {
    val cells = cellsWithin(x, y, radius)
    val perCell = motes * DELTA_PER_MOTE / cells.size
    cells.forEach { addTo(it, perCell) }
  }

  @Synchronized
  fun addToCell(cell: Cell, motes: Double) {
    addTo(cell, motes * DELTA_PER_MOTE)
  }

  @Synchronized
  fun setDelta(cell: Cell, delta: Double) {
    tileOf(cell.x, cell.y)[indexOf(cell.x, cell.y)] = delta
    changed += tileKeyOf(cell.x, cell.y)
  }

  @Synchronized
  fun cellsWithin(x: Double, y: Double, radius: Double): List<Cell> {
    val centre = Cell(cellOf(x), cellOf(y))
    val reach = (radius / CELL_METRES).toInt() + 1
    val inside = ArrayList<Cell>()

    for (cy in centre.y - reach..centre.y + reach) {
      for (cx in centre.x - reach..centre.x + reach) {
        val dx = (cx + 0.5) * CELL_METRES - x
        val dy = (cy + 0.5) * CELL_METRES - y
        if (dx * dx + dy * dy <= radius * radius) inside += Cell(cx, cy)
      }
    }

    return inside.ifEmpty { listOf(centre) }
  }

  /** Every cell holding a delta, for the field rule to walk. */
  @Synchronized
  internal fun tileKeys(): List<Long> = tiles.keys.toList()

  @Synchronized
  internal fun tileArray(key: Long): DoubleArray? = tiles[key]

  @Synchronized
  internal fun ensureTile(key: Long): DoubleArray = tiles.getOrPut(key) { DoubleArray(TILE_CELLS * TILE_CELLS) }

  @Synchronized
  internal fun markChanged(key: Long) {
    changed += key
  }

  @Synchronized
  internal fun drop(key: Long) {
    if (tiles.remove(key) == null) return
    changed -= key
    dropped += key
  }

  /** Hands the tiles to write and to delete since the last call, and forgets them. */
  @Synchronized
  fun takeChanges(): Changes {
    val written = changed.mapNotNull { key -> tiles[key]?.let { key to it.copyOf() } }.toMap()
    val gone = dropped.toSet()
    changed.clear()
    dropped.clear()

    return Changes(written, gone)
  }

  /** Puts a saved tile back. Boot only. */
  @Synchronized
  fun restore(key: Long, delta: DoubleArray) {
    require(delta.size == TILE_CELLS * TILE_CELLS) { "A mana tile holds ${TILE_CELLS * TILE_CELLS} cells" }
    tiles[key] = delta
  }

  @Synchronized
  fun clear() {
    tiles.clear()
    changed.clear()
    dropped.clear()
  }

  /** The largest change on a tile, so the field rule can drop tiles that have faded out. */
  @Synchronized
  internal fun peakOf(tile: DoubleArray): Double = tile.fold(0.0) { peak, delta -> max(peak, abs(delta)) }

  @Synchronized
  private fun addTo(cell: Cell, delta: Double) {
    tileOf(cell.x, cell.y)[indexOf(cell.x, cell.y)] += delta
    changed += tileKeyOf(cell.x, cell.y)
  }

  private fun tileOf(cellX: Int, cellY: Int): DoubleArray = ensureTile(tileKeyOf(cellX, cellY))

  data class Cell(val x: Int, val y: Int) {
    val centreX: Double get() = (x + 0.5) * CELL_METRES
    val centreY: Double get() = (y + 0.5) * CELL_METRES
  }

  class Changes(val written: Map<Long, DoubleArray>, val dropped: Set<Long>)

  companion object {
    const val CELL_METRES = 128.0
    const val TILE_CELLS = 32

    /** One mote is a thousandth of density on one cell, the unit every source counts in. */
    const val DELTA_PER_MOTE = 0.001

    fun cellOf(metres: Double): Int = floor(metres / CELL_METRES).toInt()

    fun tileKeyOf(cellX: Int, cellY: Int): Long =
      tileKey(Math.floorDiv(cellX, TILE_CELLS), Math.floorDiv(cellY, TILE_CELLS))

    fun tileKey(tileX: Int, tileY: Int): Long = (tileX.toLong() shl 32) or (tileY.toLong() and 0xFFFFFFFFL)

    fun tileXOf(key: Long): Int = (key shr 32).toInt()

    fun tileYOf(key: Long): Int = key.toInt()

    fun indexOf(cellX: Int, cellY: Int): Int =
      Math.floorMod(cellY, TILE_CELLS) * TILE_CELLS + Math.floorMod(cellX, TILE_CELLS)
  }
}
