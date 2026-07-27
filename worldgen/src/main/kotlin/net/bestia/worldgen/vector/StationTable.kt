package net.bestia.worldgen.vector

/**
 * Per-station attributes of a vector feature: one row per centerline vertex, one column per named
 * channel (`width`, `depth`, `floor_elevation`, ...).
 *
 * Interpolation is uniform Catmull-Rom over the station parameter `u` that [Polyline.project]
 * returns, which makes it a pure function of position along the line. It must never be a function
 * of anything chunk-local - "interpolate between the two stations nearest this chunk" is exactly
 * the bug that produces attribute jumps at chunk borders.
 *
 * Values are stored channel-major in one flat array so that sampling all channels at a given `u`
 * touches contiguous memory per channel and allocates nothing.
 */
class StationTable private constructor(
  val channelNames: List<String>,
  val stationCount: Int,
  private val values: DoubleArray
) {

  val channelCount get() = channelNames.size

  /**
   * Index of the named channel, for hoisting out of the per-column loop.
   *
   * @throws IllegalArgumentException if the channel does not exist - a feature asking for an
   *   attribute its stations do not carry is a construction bug, not a runtime condition.
   */
  fun channel(name: String): Int {
    val idx = channelNames.indexOf(name)
    require(idx >= 0) { "No station channel '$name'; have $channelNames" }
    return idx
  }

  fun valueAt(channel: Int, station: Int) = values[channel * stationCount + station]

  /** Interpolated value of a single channel at station parameter [u]. */
  fun sample(channel: Int, u: Double): Double {
    val clamped = u.coerceIn(0.0, (stationCount - 1).toDouble())
    val i = clamped.toInt().coerceAtMost(stationCount - 2).coerceAtLeast(0)
    val t = clamped - i
    return sampleChannel(channel, i, t)
  }

  /**
   * Interpolates every channel at [u] into [out], which must have [channelCount] entries.
   *
   * This is the hot path: one call per candidate feature per voxel column.
   */
  fun sampleInto(u: Double, out: DoubleArray) {
    require(out.size >= channelCount) { "out needs $channelCount slots, had ${out.size}" }

    val clamped = u.coerceIn(0.0, (stationCount - 1).toDouble())
    val i = clamped.toInt().coerceAtMost(stationCount - 2).coerceAtLeast(0)
    val t = clamped - i

    for (c in 0 until channelCount) {
      out[c] = sampleChannel(c, i, t)
    }
  }

  private fun sampleChannel(channel: Int, i: Int, t: Double): Double {
    if (stationCount == 1) return values[channel * stationCount]

    val base = channel * stationCount
    // Ends are clamped rather than extrapolated: a river must not gain width past its mouth
    // because the spline overshot.
    val p0 = values[base + (i - 1).coerceAtLeast(0)]
    val p1 = values[base + i]
    val p2 = values[base + (i + 1).coerceAtMost(stationCount - 1)]
    val p3 = values[base + (i + 2).coerceAtMost(stationCount - 1)]

    return Polyline.catmullRom(p0, p1, p2, p3, t)
  }

  override fun toString() = "StationTable[stations=$stationCount, channels=$channelNames]"

  class Builder(private val stationCount: Int) {
    private val names = ArrayList<String>()
    private val columns = ArrayList<DoubleArray>()

    init {
      require(stationCount >= 1) { "A station table needs at least one station" }
    }

    fun channel(name: String, values: DoubleArray): Builder {
      require(values.size == stationCount) {
        "Channel '$name' has ${values.size} values but the table has $stationCount stations"
      }
      require(name !in names) { "Duplicate station channel '$name'" }
      names.add(name)
      columns.add(values.copyOf())
      return this
    }

    /** Fills a channel from a function of station index - convenient for constant or derived channels. */
    fun channel(name: String, value: (station: Int) -> Double): Builder =
      channel(name, DoubleArray(stationCount) { value(it) })

    fun build(): StationTable {
      require(names.isNotEmpty()) { "A station table needs at least one channel" }

      val flat = DoubleArray(names.size * stationCount)
      for (c in columns.indices) {
        columns[c].copyInto(flat, c * stationCount)
      }

      return StationTable(names.toList(), stationCount, flat)
    }
  }
}
