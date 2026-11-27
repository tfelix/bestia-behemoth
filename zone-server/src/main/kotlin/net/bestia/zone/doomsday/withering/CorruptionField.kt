package net.bestia.zone.doomsday.withering

import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.weather.CorruptionExposure
import net.bestia.zone.worldevent.gm.CorruptionReading
import org.springframework.stereotype.Component

/**
 * How corrupted the land is, from 0 to 1, on the mana field's 128 m cells and 4 km tiles. A tile exists only where the
 * Withering reached, so an untouched world holds none. Guarded by its own monitor, like the mana field.
 */
@Component
class CorruptionField : CorruptionReading, CorruptionExposure {

  private val tiles = HashMap<Long, DoubleArray>()
  private val changed = HashSet<Long>()
  private val dropped = HashSet<Long>()

  val tileCount: Int
    @Synchronized get() = tiles.size

  override fun corruptionAt(x: Double, y: Double): Double {
    return intensityAt(x, y)
  }

  @Synchronized
  fun intensityAt(x: Double, y: Double): Double {
    return intensityOf(Cell(ManaField.cellOf(x), ManaField.cellOf(y)))
  }

  @Synchronized
  fun intensityOf(cell: Cell): Double {
    val tile = tiles[ManaField.tileKeyOf(cell.x, cell.y)] ?: return 0.0
    return tile[ManaField.indexOf(cell.x, cell.y)]
  }

  /** Moves the cell by [delta], kept within 0 and 1. */
  @Synchronized
  fun change(cell: Cell, delta: Double) {
    val key = ManaField.tileKeyOf(cell.x, cell.y)
    val tile = tiles[key] ?: if (delta <= 0.0) return else DoubleArray(CELLS).also { tiles[key] = it }
    val index = ManaField.indexOf(cell.x, cell.y)
    tile[index] = (tile[index] + delta).coerceIn(0.0, 1.0)
    changed += key
  }

  /** Every cell that holds any corruption, with its intensity. */
  @Synchronized
  fun corrupted(): List<Pair<Cell, Double>> {
    val found = ArrayList<Pair<Cell, Double>>()
    for ((key, tile) in tiles) {
      val tileX = ManaField.tileXOf(key) * ManaField.TILE_CELLS
      val tileY = ManaField.tileYOf(key) * ManaField.TILE_CELLS
      tile.forEachIndexed { i, intensity ->
        if (intensity > 0.0) {
          found += Cell(tileX + i % ManaField.TILE_CELLS, tileY + i / ManaField.TILE_CELLS) to intensity
        }
      }
    }
    return found
  }

  /** Forgets the tiles that healed completely. */
  @Synchronized
  fun dropHealed() {
    val healed = tiles.filterValues { tile -> tile.all { it <= 0.0 } }.keys
    healed.forEach {
      tiles.remove(it)
      changed -= it
      dropped += it
    }
  }

  /** Hands the tiles to write and to delete since the last call, and forgets them. */
  @Synchronized
  fun takeChanges(): ManaField.Changes {
    val written = changed.mapNotNull { key -> tiles[key]?.let { key to it.copyOf() } }.toMap()
    val gone = dropped.toSet()
    changed.clear()
    dropped.clear()
    return ManaField.Changes(written, gone)
  }

  /** Puts a saved tile back. Boot only. */
  @Synchronized
  fun restore(key: Long, cells: DoubleArray) {
    require(cells.size == CELLS) { "A corruption tile holds $CELLS cells" }
    tiles[key] = cells
  }

  @Synchronized
  fun clear() {
    tiles.clear()
    changed.clear()
    dropped.clear()
  }

  private companion object {
    const val CELLS = ManaField.TILE_CELLS * ManaField.TILE_CELLS
  }
}
