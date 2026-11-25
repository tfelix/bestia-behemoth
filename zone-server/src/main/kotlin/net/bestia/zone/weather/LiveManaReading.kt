package net.bestia.zone.weather

import net.bestia.worldgen.climate.WeatherModel
import net.bestia.worldgen.climate.WeatherRegion
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaField.Companion.CELL_METRES
import net.bestia.zone.world.mana.ManaField.Companion.TILE_CELLS
import kotlin.math.floor

/**
 * The live mana density each weather region's storms read: the 95th percentile of the density over the region,
 * sampled once a kilometre, as the worldgen summary does for a fresh world.
 *
 * The start density is sampled once. A [refresh] adds the field's change only where tiles exist, and recomputes
 * only the regions those tiles touch, so it costs nothing on an untouched world. Between refreshes the reading
 * stays frozen, which keeps a forecast true for the rest of the Bestia hour.
 */
class LiveManaReading(
  regionCount: Int,
  regionAt: (Double, Double) -> Int,
  width: Double,
  height: Double,
  startDensityAt: (Double, Double) -> Double,
) : WeatherModel.ManaReading {

  private val columns = floor(width / SAMPLE_METRES).toInt()
  private val rows = floor(height / SAMPLE_METRES).toInt()

  private val regionOfSample = IntArray(columns * rows)
  private val startOfSample = DoubleArray(columns * rows)
  private val samplesOfRegion: Array<IntArray>
  private val startPeak: DoubleArray

  @Volatile
  private var peak: DoubleArray

  init {
    for (row in 0 until rows) {
      for (column in 0 until columns) {
        val sample = row * columns + column
        val x = (column + 0.5) * SAMPLE_METRES
        val y = (row + 0.5) * SAMPLE_METRES
        regionOfSample[sample] = regionAt(x, y)
        startOfSample[sample] = startDensityAt(x, y)
      }
    }

    val grouped = regionOfSample.indices.groupBy { regionOfSample[it] }
    samplesOfRegion = Array(regionCount) { grouped[it]?.toIntArray() ?: IntArray(0) }
    startPeak = DoubleArray(regionCount) { region -> peakOf(samplesOfRegion[region]) { startOfSample[it] } }
    peak = startPeak
  }

  override fun of(region: WeatherRegion): Double = peakOf(region.index)

  fun peakOf(regionIndex: Int): Double = peak[regionIndex]

  /** Freezes the field as it is now. */
  fun refresh(field: ManaField) {
    val change = HashMap<Int, Double>()
    synchronized(field) { field.tileKeys().forEach { key -> collectChange(field, key, change) } }

    val touched = change.keys.map { regionOfSample[it] }.toSet()
    val next = startPeak.copyOf()
    touched.forEach { region ->
      next[region] = peakOf(samplesOfRegion[region]) { startOfSample[it] + (change[it] ?: 0.0) }
    }

    peak = next
  }

  /** The mean change over each kilometre sample inside one tile. */
  private fun collectChange(field: ManaField, tileKey: Long, into: MutableMap<Int, Double>) {
    val tileMetres = TILE_CELLS * CELL_METRES
    val fromX = ManaField.tileXOf(tileKey) * tileMetres
    val fromY = ManaField.tileYOf(tileKey) * tileMetres

    val firstColumn = floor(fromX / SAMPLE_METRES).toInt().coerceAtLeast(0)
    val lastColumn = floor((fromX + tileMetres) / SAMPLE_METRES).toInt().coerceAtMost(columns - 1)
    val firstRow = floor(fromY / SAMPLE_METRES).toInt().coerceAtLeast(0)
    val lastRow = floor((fromY + tileMetres) / SAMPLE_METRES).toInt().coerceAtMost(rows - 1)

    for (row in firstRow..lastRow) {
      for (column in firstColumn..lastColumn) {
        val mean = meanChangeOver(field, column * SAMPLE_METRES, row * SAMPLE_METRES)
        if (mean != 0.0) into.merge(row * columns + column, mean) { a, _ -> a }
      }
    }
  }

  private fun meanChangeOver(field: ManaField, x: Double, y: Double): Double {
    val fromCellX = ManaField.cellOf(x)
    val toCellX = ManaField.cellOf(x + SAMPLE_METRES - 1)
    val fromCellY = ManaField.cellOf(y)
    val toCellY = ManaField.cellOf(y + SAMPLE_METRES - 1)

    var sum = 0.0
    var count = 0
    for (cellY in fromCellY..toCellY) {
      for (cellX in fromCellX..toCellX) {
        sum += field.deltaOf(cellX, cellY)
        count++
      }
    }
    return sum / count
  }

  private inline fun peakOf(samples: IntArray, density: (Int) -> Double): Double {
    if (samples.isEmpty()) return 0.0
    val sorted = DoubleArray(samples.size) { density(samples[it]) }.also { it.sort() }
    return sorted[(PEAK_SHARE * (sorted.size - 1)).toInt()]
  }

  private companion object {
    const val SAMPLE_METRES = 1000.0
    const val PEAK_SHARE = 0.95
  }
}
