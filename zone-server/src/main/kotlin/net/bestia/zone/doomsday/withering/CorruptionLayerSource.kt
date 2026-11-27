package net.bestia.zone.doomsday.withering

import net.bestia.zone.ground.ColumnKey
import net.bestia.zone.ground.ColumnLevels
import net.bestia.zone.ground.GroundLayer
import net.bestia.zone.ground.GroundLayerEncoding
import net.bestia.zone.ground.GroundLayerEntry
import net.bestia.zone.ground.GroundLayerSource
import net.bestia.zone.ground.GroundOverlayService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaField.Cell
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.math.floor

/**
 * Presents the Withering's rot as a ground layer, so the client draws creep on every square metre of it.
 *
 * The field is 128 m coarse. Each metre blends the four nearest cells and adds a little fixed noise, so the
 * creep's edge is ragged and moves forward a few metres at a time as the cells behind it grow.
 */
@Service
class CorruptionLayerSource(
  private val field: CorruptionField,
  private val worldService: WorldService,
  /** `@Lazy`, because the overlay collects every layer source, this one too. */
  @Lazy private val overlay: GroundOverlayService,
) : GroundLayerSource {

  override val layer = GroundLayer.CORRUPTED

  private val retired = GroundLayerEntry.retired(layer, GroundLayerEncoding.NIBBLE_V1)

  override fun entryAt(columnKey: Long): GroundLayerEntry {
    val size = worldService.config.chunkSize
    val minX = ColumnKey.chunkXOf(columnKey) * size
    val minY = ColumnKey.chunkYOf(columnKey) * size
    val near = NearCells.around(field, minX, minY, size) ?: return retired

    val levels = ColumnLevels(size)
    for (localY in 0 until size) {
      for (localX in 0 until size) {
        val level = levelAt(near, minX + localX, minY + localY)
        if (level > 0) {
          levels.add(localX, localY, level)
        }
      }
    }
    if (levels.isEmpty) {
      return retired
    }
    return GroundLayerEntry(layer, GroundLayerEncoding.NIBBLE_V1, levels.toNibbles())
  }

  /** The field changes off the tick, from the Withering and from the Blight Bloom alike, so this asks it. */
  @Scheduled(fixedDelay = ANNOUNCE_EVERY_MS, initialDelay = ANNOUNCE_EVERY_MS)
  fun announceChanges() {
    val size = worldService.config.chunkSize
    val columns = HashSet<Long>()
    for (cell in field.takeChangedCells()) {
      columnsReaching(cell, size, columns)
    }
    columns.forEach { overlay.markLayerDirty(it, layer) }
  }

  /** A cell's metres blend with half of each neighbour, so its change reaches that far. */
  private fun columnsReaching(cell: Cell, size: Int, into: MutableSet<Long>) {
    val reach = ManaField.CELL_METRES / 2
    val minX = floor((cell.x * ManaField.CELL_METRES - reach) / size).toInt()
    val maxX = floor(((cell.x + 1) * ManaField.CELL_METRES + reach) / size).toInt()
    val minY = floor((cell.y * ManaField.CELL_METRES - reach) / size).toInt()
    val maxY = floor(((cell.y + 1) * ManaField.CELL_METRES + reach) / size).toInt()
    for (chunkY in minY..maxY) {
      for (chunkX in minX..maxX) {
        into += ColumnKey.of(chunkX, chunkY)
      }
    }
  }

  /** The cells whose centres surround one column, read once so a column takes the field's lock once. */
  private class NearCells(private val originX: Int, private val originY: Int, private val intensity: Array<DoubleArray>) {

    fun at(cellX: Int, cellY: Int): Double {
      return intensity[cellY - originY][cellX - originX]
    }

    companion object {

      fun around(field: CorruptionField, minX: Int, minY: Int, size: Int): NearCells? {
        val originX = centreBelow(minX + 0.5)
        val originY = centreBelow(minY + 0.5)
        val width = centreBelow(minX + size - 0.5) - originX + 2
        val height = centreBelow(minY + size - 0.5) - originY + 2

        var any = false
        val intensity = Array(height) { dy ->
          DoubleArray(width) { dx -> field.intensityOf(Cell(originX + dx, originY + dy)).also { any = any || it > 0.0 } }
        }
        return if (any) NearCells(originX, originY, intensity) else null
      }
    }
  }

  companion object {
    private const val ANNOUNCE_EVERY_MS = 30_000L

    /** Below this the land shows nothing. */
    const val VISIBLE_FROM = 0.2

    /** From here the creep covers the ground fully. Near the line where the Withering status catches. */
    const val FULL_AT = 0.45

    /** How far the fixed noise moves a metre's intensity, which is what makes the edge ragged. */
    private const val RAGGEDNESS = 0.1

    /** The index of the cell whose centre is the nearest at or below this metre. */
    private fun centreBelow(metres: Double): Int {
      return floor(metres / ManaField.CELL_METRES - 0.5).toInt()
    }

    private fun levelAt(near: NearCells, x: Int, y: Int): Int {
      val u = (x + 0.5) / ManaField.CELL_METRES - 0.5
      val v = (y + 0.5) / ManaField.CELL_METRES - 0.5
      val cellX = floor(u).toInt()
      val cellY = floor(v).toInt()
      val fx = u - cellX
      val fy = v - cellY

      val top = near.at(cellX, cellY) * (1 - fx) + near.at(cellX + 1, cellY) * fx
      val bottom = near.at(cellX, cellY + 1) * (1 - fx) + near.at(cellX + 1, cellY + 1) * fx
      val blended = top * (1 - fy) + bottom * fy
      return levelOf(blended + RAGGEDNESS * edgeNoise(x, y))
    }

    /** How strongly a metre with this intensity shows the creep, as a [ColumnLevels] level. */
    fun levelOf(intensity: Double): Int {
      val shown = ((intensity - VISIBLE_FROM) / (FULL_AT - VISIBLE_FROM)).coerceIn(0.0, 1.0)
      return (shown * MAX_LEVEL).toInt()
    }

    /** Fixed value noise in -1..1: a broad wobble every 8 m with a finer one every 2 m on top. */
    fun edgeNoise(x: Int, y: Int): Double {
      return 0.7 * valueNoise(x / 8.0, y / 8.0) + 0.3 * valueNoise(x / 2.0, y / 2.0)
    }

    private fun valueNoise(x: Double, y: Double): Double {
      val ix = floor(x).toInt()
      val iy = floor(y).toInt()
      val fx = smooth(x - ix)
      val fy = smooth(y - iy)
      val top = hash(ix, iy) * (1 - fx) + hash(ix + 1, iy) * fx
      val bottom = hash(ix, iy + 1) * (1 - fx) + hash(ix + 1, iy + 1) * fx
      return top * (1 - fy) + bottom * fy
    }

    private fun smooth(t: Double): Double {
      return t * t * (3 - 2 * t)
    }

    /** A lattice point's value in -1..1. */
    private fun hash(x: Int, y: Int): Double {
      var h = x * 374_761_393 + y * 668_265_263
      h = (h xor (h ushr 13)) * 1_274_126_177
      h = h xor (h ushr 16)
      return (h and 0xFFFF) / 32_767.5 - 1.0
    }

    private const val MAX_LEVEL = 255
  }
}
