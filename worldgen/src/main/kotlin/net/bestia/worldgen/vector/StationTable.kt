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
 *
 * ### Open and periodic
 *
 * An open table belongs to a [Polyline] and a periodic one to a [Ring], and the difference is not only
 * that `u` wraps. It is that a periodic table's *slope* is continuous at the seam as well as its value:
 * the neighbour lookups the Catmull-Rom basis needs go through `floorMod` instead of clamping, so station
 * 0 sees the last station as its left neighbour rather than seeing itself. Clamping there would flatten
 * the curve over the last segment and leave a visible kink at one vertex of every lake shore.
 *
 * **Mind the segment count.** A periodic table has *n* segments for *n* stations where an open one has
 * *n - 1*, because the segment from the last station back to the first is real. `AreaFeature` requires
 * `perimeter.stationCount == ring.vertexCount` for that reason. Getting it wrong rotates a shore's
 * attributes by one vertex, which no assertion here would catch and which is visible only on a PNG.
 */
class StationTable private constructor(
  val channelNames: List<String>,
  val stationCount: Int,
  /**
   * Whether `u` wraps.
   *
   * Part of the table's identity rather than a sampling option, and it must survive any export: a
   * periodic table decoded as an open one samples differently at exactly one segment, which is a silent
   * shape change with no error attached to it.
   */
  val periodic: Boolean,
  private val values: DoubleArray
) {

  val channelCount get() = channelNames.size

  /** Segments between stations: [stationCount] when [periodic], one fewer when open. */
  val segmentCount get() = if (periodic) stationCount else stationCount - 1

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
    val i = segmentOf(u)
    return sampleChannel(channel, i, positionIn(u, i))
  }

  /**
   * Interpolates every channel at [u] into [out], which must have [channelCount] entries.
   *
   * This is the hot path: one call per candidate feature per voxel column.
   */
  fun sampleInto(u: Double, out: DoubleArray) {
    require(out.size >= channelCount) { "out needs $channelCount slots, had ${out.size}" }

    val i = segmentOf(u)
    val t = positionIn(u, i)

    for (c in 0 until channelCount) {
      out[c] = sampleChannel(c, i, t)
    }
  }

  /** Index of the station at or before [u], clamped for an open table and wrapped for a periodic one. */
  private fun segmentOf(u: Double): Int {
    if (!periodic) {
      val clamped = u.coerceIn(0.0, (stationCount - 1).toDouble())
      return clamped.toInt().coerceAtMost(stationCount - 2).coerceAtLeast(0)
    }
    if (stationCount == 1) return 0
    val wrapped = wrap(u)
    return wrapped.toInt().coerceIn(0, stationCount - 1)
  }

  private fun positionIn(u: Double, segment: Int): Double =
    if (periodic) wrap(u) - segment else u.coerceIn(0.0, (stationCount - 1).toDouble()) - segment

  /** [u] brought into `[0, stationCount)`. A negative station parameter is as legal as any other. */
  private fun wrap(u: Double): Double {
    val n = stationCount.toDouble()
    val m = u % n
    return if (m < 0.0) m + n else m
  }

  private fun sampleChannel(channel: Int, i: Int, t: Double): Double {
    if (stationCount == 1) return values[channel * stationCount]

    val base = channel * stationCount
    val p0: Double
    val p1: Double
    val p2: Double
    val p3: Double

    if (periodic) {
      // Wrapped neighbours, so the spline's slope is continuous across the seam and not merely its value.
      val n = stationCount
      p0 = values[base + Math.floorMod(i - 1, n)]
      p1 = values[base + Math.floorMod(i, n)]
      p2 = values[base + Math.floorMod(i + 1, n)]
      p3 = values[base + Math.floorMod(i + 2, n)]
    } else {
      // Ends are clamped rather than extrapolated: a river must not gain width past its mouth
      // because the spline overshot.
      p0 = values[base + (i - 1).coerceAtLeast(0)]
      p1 = values[base + i]
      p2 = values[base + (i + 1).coerceAtMost(stationCount - 1)]
      p3 = values[base + (i + 2).coerceAtMost(stationCount - 1)]
    }

    return Polyline.catmullRom(p0, p1, p2, p3, t)
  }

  override fun toString() =
    "StationTable[stations=$stationCount${if (periodic) " periodic" else ""}, channels=$channelNames]"

  /**
   * @param periodic true for a table indexed by a [RingProjection], where station `n` is station `0`.
   *   Defaulted to false so that every existing call site keeps the open arithmetic **exactly** - which
   *   is the guard on this change: `StationTableTest` must stay green without being touched.
   */
  class Builder(private val stationCount: Int, private val periodic: Boolean = false) {
    private val names = ArrayList<String>()
    private val columns = ArrayList<DoubleArray>()

    init {
      require(stationCount >= 1) { "A station table needs at least one station" }
      require(!periodic || stationCount >= 3) {
        "A periodic table needs at least three stations to be a closed curve, was $stationCount"
      }
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

      return StationTable(names.toList(), stationCount, periodic, flat)
    }
  }
}
