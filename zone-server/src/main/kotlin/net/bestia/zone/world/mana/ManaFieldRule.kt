package net.bestia.zone.world.mana

import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaField.Companion.CELL_METRES
import net.bestia.zone.world.mana.ManaField.Companion.TILE_CELLS
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/**
 * What happens to the mana field while nobody touches it: it creeps, leaks and drains out of the start towns.
 *
 * The field holds only the change over the start density, so a leak on that change is what returns the land
 * to its natural level. Nothing has to emit the start density back.
 */
class ManaFieldRule(
  private val config: ManaFieldConfig,
  private val realSecondsPerBestiaDay: Double,
) {

  /** The land under the field: what it starts at, and whether it drains. */
  interface Ground {
    fun startDensity(cell: Cell): Double
    fun isSanctuary(cell: Cell): Boolean
  }

  /** Square metres per real second, so that the spread after a week is [ManaFieldConfig.creepMetresPerWeek]. */
  private val diffusivity = config.creepMetresPerWeek.pow(2) / (2.0 * SECONDS_PER_WEEK)

  fun step(field: ManaField, ground: Ground, seconds: Double) {
    if (field.tileCount == 0) return

    creep(field, seconds)
    leak(field, seconds)
    drainSanctuaries(field, ground, seconds)
    dropFaded(field)
  }

  private fun creep(field: ManaField, seconds: Double) {
    val share = diffusivity * seconds / (CELL_METRES * CELL_METRES)
    val substeps = ceil(share / STABLE_SHARE).toInt().coerceAtLeast(1)
    repeat(substeps) { creepOnce(field, share / substeps) }
  }

  /**
   * One explicit diffusion step. A missing tile counts as holding what its neighbour holds, so no mana flows
   * into nothing; a tile whose edge carries mana gets its neighbour created first, so it can flow on.
   */
  private fun creepOnce(field: ManaField, share: Double) {
    field.tileKeys().forEach { key -> openNeighboursOf(field, key) }

    val next = field.tileKeys().associateWith { key -> creptTile(field, key, share) }
    next.forEach { (key, cells) ->
      cells.copyInto(field.tileArray(key)!!)
      field.markChanged(key)
    }
  }

  private fun creptTile(field: ManaField, key: Long, share: Double): DoubleArray {
    val tile = field.tileArray(key)!!
    val originX = ManaField.tileXOf(key) * TILE_CELLS
    val originY = ManaField.tileYOf(key) * TILE_CELLS

    return DoubleArray(tile.size) { index ->
      val x = originX + index % TILE_CELLS
      val y = originY + index / TILE_CELLS
      val here = tile[index]
      val around = neighbour(field, x - 1, y, here) + neighbour(field, x + 1, y, here) +
        neighbour(field, x, y - 1, here) + neighbour(field, x, y + 1, here)

      here + share * (around - 4 * here)
    }
  }

  private fun neighbour(field: ManaField, cellX: Int, cellY: Int, fallback: Double): Double {
    val tile = field.tileArray(ManaField.tileKeyOf(cellX, cellY)) ?: return fallback
    return tile[ManaField.indexOf(cellX, cellY)]
  }

  private fun openNeighboursOf(field: ManaField, key: Long) {
    val tile = field.tileArray(key) ?: return
    Side.entries
      .filter { edgePeak(tile, it) >= FADED }
      .forEach { field.ensureTile(it.neighbourOf(key)) }
  }

  private fun leak(field: ManaField, seconds: Double) {
    val kept = halving(seconds, config.leakHalfLifeDays * SECONDS_PER_DAY)

    field.tileKeys().forEach { key ->
      val tile = field.tileArray(key)!!
      for (i in tile.indices) tile[i] *= kept
      field.markChanged(key)
    }
  }

  /** Only mana added on top drains, and never below what the land holds by nature. */
  private fun drainSanctuaries(field: ManaField, ground: Ground, seconds: Double) {
    val kept = halving(seconds, config.sanctuaryHalfLifeBestiaDays * realSecondsPerBestiaDay)

    field.tileKeys().forEach { key ->
      val tile = field.tileArray(key)!!
      for (i in tile.indices) {
        if (tile[i] <= 0.0) continue

        val cell = cellOf(key, i)
        if (!ground.isSanctuary(cell)) continue

        val start = ground.startDensity(cell)
        val floor = max(config.sanctuaryTarget, start)
        val excess = start + tile[i] - floor
        if (excess > 0.0) tile[i] = floor + excess * kept - start
      }
    }
  }

  /** A tile is dropped once nothing on it is worth a mote and no neighbour still pours into it. */
  private fun dropFaded(field: ManaField) {
    field.tileKeys()
      .filter { key -> field.peakOf(field.tileArray(key)!!) < FADED && !fedByNeighbour(field, key) }
      .forEach(field::drop)
  }

  private fun fedByNeighbour(field: ManaField, key: Long): Boolean = Side.entries.any { side ->
    val neighbour = field.tileArray(side.neighbourOf(key)) ?: return@any false
    edgePeak(neighbour, side.opposite) >= FADED
  }

  private fun edgePeak(tile: DoubleArray, side: Side): Double =
    (0 until TILE_CELLS).maxOf { along -> abs(tile[side.edgeIndex(along)]) }

  private fun cellOf(key: Long, index: Int): Cell =
    Cell(ManaField.tileXOf(key) * TILE_CELLS + index % TILE_CELLS, ManaField.tileYOf(key) * TILE_CELLS + index / TILE_CELLS)

  private fun halving(seconds: Double, halfLife: Double): Double = 0.5.pow(seconds / halfLife)

  private enum class Side(val dx: Int, val dy: Int) {
    WEST(-1, 0), EAST(1, 0), SOUTH(0, -1), NORTH(0, 1);

    val opposite: Side get() = entries.first { it.dx == -dx && it.dy == -dy }

    fun neighbourOf(key: Long): Long = ManaField.tileKey(ManaField.tileXOf(key) + dx, ManaField.tileYOf(key) + dy)

    fun edgeIndex(along: Int): Int = when (this) {
      WEST -> along * TILE_CELLS
      EAST -> along * TILE_CELLS + TILE_CELLS - 1
      SOUTH -> along
      NORTH -> (TILE_CELLS - 1) * TILE_CELLS + along
    }
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400.0
    const val SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY

    /** Explicit diffusion stays stable while a cell passes on less than a quarter per step. */
    const val STABLE_SHARE = 0.2

    /** Half a mote: below it a cell counts as untouched. */
    const val FADED = 0.5 * ManaField.DELTA_PER_MOTE
  }
}
