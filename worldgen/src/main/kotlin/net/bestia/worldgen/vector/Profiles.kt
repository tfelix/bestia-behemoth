package net.bestia.worldgen.vector

import net.bestia.worldgen.fields.Noise
import kotlin.math.abs
import kotlin.math.pow

/**
 * The cross-section library. Every profile here is a pure function of `(lateral, u, station, base)`
 * and allocates nothing, so it is safe to share one instance across every thread generating chunks.
 *
 * Channel names are conventions, not magic: a feature declares whichever channels its profile
 * reads, and the profile resolves their indices once at construction time rather than doing a
 * string lookup per voxel column.
 */
object Profiles {

  const val CHANNEL_CORRIDOR = PolylineFeature.CORRIDOR_CHANNEL
  const val CHANNEL_BED_ELEVATION = "bed_elevation"
  const val CHANNEL_CURVATURE = "curvature"
  const val CHANNEL_WIDTH = "width"
  const val CHANNEL_DEPTH = "depth"
  const val CHANNEL_BANK_HEIGHT = "bank_height"
  const val CHANNEL_FLOOR_ELEVATION = "floor_elevation"
  const val CHANNEL_HALF_WIDTH_FLOOR = "half_width_floor"
  const val CHANNEL_WALL_HEIGHT = "wall_height"
  const val CHANNEL_WALL_EXPONENT = "wall_exponent"
  const val CHANNEL_SURFACE_ELEVATION = "surface_elevation"
  const val CHANNEL_HALF_WIDTH = "half_width"
  const val CHANNEL_SHOULDER = "shoulder"
  const val CHANNEL_RIDGE_HEIGHT = "ridge_height"

  /**
   * How a river channel departs from a perfect extruded parabola. See [riverChannel].
   *
   * @param thalwegOffset how far the deepest line may sit off centre on a full bend, as a fraction of the
   *   half-width. Must stay below 1, or the thalweg reaches the bank and the parabola on that side
   *   collapses to zero span.
   * @param bendScale dimensionless bend tightness - `curvature * width` - at which half of [thalwegOffset]
   *   is reached.
   *
   *   An Earth meander at its apex has a radius of two or three channel widths, which is a tightness of
   *   0.3 to 0.5, and that is what this was first set to. Measured on a real reach afterwards, the rivers
   *   this pipeline actually produces are far lazier than that: a 14 m channel with a 360 m meander
   *   wavelength and 20 m of amplitude peaks at a tightness of about **0.085**, so a scale of 0.3 put every
   *   bend in the world down in the flat foot of the response and moved the thalweg by 0.7 m - less than a
   *   voxel, and invisible. 0.1 puts this pipeline's real bends near the half-response point.
   *
   *   The honest reading is that the *meanders* are too gentle rather than the response too weak; see
   *   [net.bestia.worldgen.hydro.HydrologyParams.meanderWidthFactor]. This makes the asymmetry visible at
   *   the curvature that exists today, and should be raised back towards 0.3 if the meanders are ever
   *   tightened.
   * @param roughness metres the bank and bed wander in or out. Zero disables the noise entirely.
   * @param roughnessWavelength metres between wobbles, along the river and across it.
   * @param seed the roughness field; per feature, so two rivers do not share a bank pattern.
   */
  data class ChannelShape(
    val thalwegOffset: Double = 0.0,
    val bendScale: Double = 0.1,
    val roughness: Double = 0.0,
    val roughnessWavelength: Double = 11.0,
    val seed: Long = 0L
  ) {
    init {
      require(thalwegOffset in 0.0..0.9) { "thalwegOffset must be in [0,0.9], was $thalwegOffset" }
      require(bendScale > 0.0) { "bendScale must be positive, was $bendScale" }
      require(roughness >= 0.0) { "roughness must not be negative, was $roughness" }
      require(roughnessWavelength > 0.0) {
        "roughnessWavelength must be positive, was $roughnessWavelength"
      }
    }

    val isPlain get() = thalwegOffset <= 0.0 && roughness <= 0.0

    /**
     * How far off centre the thalweg sits at a given bend tightness, as a share of the half-width.
     *
     * Public, and the profile below calls it rather than inlining the same expression, so a tool that wants
     * to report what the response *does* cannot drift from what it is. Re-deriving it at the call site
     * would measure the copy.
     */
    fun thalwegShareAt(tightness: Double): Double =
      thalwegOffset * tightness / (abs(tightness) + bendScale)
  }

  /**
   * River channel: a parabolic cut of `depth` below the bed elevation across `width`, wrapped in a
   * floodplain shoulder that eases back up to the surrounding terrain.
   *
   * The shoulder is what keeps a big river from looking like a slot cut into a plain. Its width
   * grows with discharge through the `shoulder` channel, so a headwater stream gets a tight V and a
   * lowland trunk gets a broad flat valley floor.
   *
   * ### Why the cross-section is not symmetric
   *
   * A profile written on `abs(lateral)` gives both banks the same shape at every station, for the whole
   * length of every river in the world. That is the single strongest tell that a channel was extruded along
   * a spline rather than cut by water: a real channel throws its deepest line against the **outer** bank of
   * every bend and drops a shallow point bar on the inner one, and the two swap over at each inflection.
   *
   * [ChannelShape.thalwegOffset] reproduces that from the signed curvature the centerline already knows.
   * The parabola is not translated - that would lift one bank off the terrain - but *stretched*: it still
   * meets the bank top at exactly `+-halfWidth`, with the span on the outer side squeezed into a steep cut
   * bank and the span on the inner side drawn out into a shelf. The bar can rise above the water surface,
   * which is a gravel bank in the middle of the river and exactly right.
   *
   * ### Why the roughness is sampled where it is
   *
   * A [HeightProfile] is handed no world position, and it may not invent one - a profile that read a chunk
   * seed would give two chunks different answers for the same column. But `(u, lateral)` *is* a continuous
   * pure function of world position, so noise sampled on that pair is a pure function of world position
   * too, and the seam guarantee survives. It also lands the noise in the right frame: distance along the
   * river and distance across it, rather than a world grid the river cuts diagonally through.
   */
  fun riverChannel(
    stations: StationTable,
    shape: ChannelShape = ChannelShape(),
    stationSpacing: Double = 1.0
  ): HeightProfile {
    val bed = stations.channel(CHANNEL_BED_ELEVATION)
    val width = stations.channel(CHANNEL_WIDTH)
    val depth = stations.channel(CHANNEL_DEPTH)
    val shoulder = stations.channel(CHANNEL_SHOULDER)
    val curvature = if (shape.thalwegOffset > 0.0) stations.channel(CHANNEL_CURVATURE) else -1

    return HeightProfile { lateral, u, station, base ->
      val halfWidth = station[width] * 0.5
      val bankTop = station[bed]

      // Roughness moves the *query* outwards or inwards rather than the geometry, so one term wobbles the
      // bank line, the bed and the foot of the shoulder together - as one channel that is not a smooth
      // shape, rather than three independently ragged ones.
      val wobble = if (shape.roughness > 0.0) {
        Noise.gradient2d(
          shape.seed,
          u * stationSpacing / shape.roughnessWavelength,
          lateral / shape.roughnessWavelength
        ) * shape.roughness
      } else {
        0.0
      }

      val d = abs(lateral) + wobble

      if (d <= halfWidth) {
        // Signed position of the deepest line, in metres off centre. Negative is right of the direction of
        // travel, which is the outer bank of a left turn - hence the minus.
        val thalweg = if (curvature >= 0) {
          -halfWidth * shape.thalwegShareAt(station[curvature] * station[width])
        } else {
          0.0
        }

        // Signed, so the two sides can be scaled independently; `wobble` is carried across from `d` so the
        // bed stays continuous with the bank it meets.
        val fromCentre = if (lateral < 0.0) -d else d
        val span = if (fromCentre >= thalweg) halfWidth - thalweg else halfWidth + thalweg
        val t = if (span > 0.0) ((fromCentre - thalweg) / span).coerceIn(-1.0, 1.0) else 1.0

        bankTop - station[depth] * (1.0 - t * t)
      } else {
        // Floodplain shoulder: ease from the bank top back to whatever the terrain was doing.
        val shoulderWidth = station[shoulder]
        if (shoulderWidth <= 0.0) {
          bankTop
        } else {
          val t = ((d - halfWidth) / shoulderWidth).coerceIn(0.0, 1.0)
          bankTop + (base - bankTop) * PolylineFeature.smoothstep(t)
        }
      }
    }
  }

  /**
   * Glacial trough: a power-law U.
   *
   * `z(d) = floor + ((|d| - half_width_floor) / (half_width - half_width_floor))^p * wall_height`
   *
   * The flat floor between the walls is the diagnostic trait that a 1 km raster cannot hold, and it
   * is the reason troughs live in the vector tier at all. An exponent near 2 gives the classic U;
   * raise it for the near-vertical walls of a young trough.
   *
   * Truncated spurs come out for free - the trough carves straight through any ridge that crosses
   * it. Hanging valleys come out for free too, because a tributary trough's floor elevation is set
   * independently of the trunk's.
   */
  fun glacialTrough(stations: StationTable): HeightProfile {
    val floor = stations.channel(CHANNEL_FLOOR_ELEVATION)
    val halfFloor = stations.channel(CHANNEL_HALF_WIDTH_FLOOR)
    val half = stations.channel(CHANNEL_HALF_WIDTH)
    val wallHeight = stations.channel(CHANNEL_WALL_HEIGHT)
    val exponent = stations.channel(CHANNEL_WALL_EXPONENT)

    return HeightProfile { lateral, _, station, _ ->
      val d = abs(lateral)
      val floorHalf = station[halfFloor]
      val outerHalf = station[half]

      when {
        d <= floorHalf -> station[floor]
        outerHalf <= floorHalf -> station[floor]
        else -> {
          val t = ((d - floorHalf) / (outerHalf - floorHalf)).coerceIn(0.0, 1.0)
          station[floor] + t.pow(station[exponent]) * station[wallHeight]
        }
      }
    }
  }

  /**
   * Road: cut and fill to a target running surface, with an embankment easing back to terrain.
   *
   * Stamped with [BlendMode.REPLACE] inside the carriageway so the surface is genuinely flat, then
   * eased over the shoulder so the earthworks do not end in a step.
   */
  fun road(stations: StationTable): HeightProfile {
    val surface = stations.channel(CHANNEL_SURFACE_ELEVATION)
    val half = stations.channel(CHANNEL_HALF_WIDTH)
    val shoulder = stations.channel(CHANNEL_SHOULDER)

    return HeightProfile { lateral, _, station, base ->
      val d = abs(lateral)
      val carriageway = station[half]

      if (d <= carriageway) {
        station[surface]
      } else {
        val shoulderWidth = station[shoulder]
        if (shoulderWidth <= 0.0) {
          station[surface]
        } else {
          val t = ((d - carriageway) / shoulderWidth).coerceIn(0.0, 1.0)
          station[surface] + (base - station[surface]) * PolylineFeature.smoothstep(t)
        }
      }
    }
  }

  /**
   * Moraine: a ridge piled on top of the terrain rather than cut into it. Use with [BlendMode.ADD].
   */
  fun moraine(stations: StationTable): HeightProfile {
    val half = stations.channel(CHANNEL_HALF_WIDTH)
    val ridgeHeight = stations.channel(CHANNEL_RIDGE_HEIGHT)

    return HeightProfile { lateral, _, station, _ ->
      val halfWidth = station[half]
      if (halfWidth <= 0.0) {
        0.0
      } else {
        val t = (abs(lateral) / halfWidth).coerceIn(0.0, 1.0)
        // Raised cosine: zero slope at the crest and at the toe, so it blends without a crease.
        station[ridgeHeight] * PolylineFeature.smoothstep(1.0 - t)
      }
    }
  }

  /**
   * Fjord: a glacial trough whose floor is below sea level, plus the sill - the shallow bar at the
   * mouth that is the defining feature of a fjord and the reason its landward basins are
   * overdeepened relative to it.
   *
   * The sill is not a separate feature because it must move with the trough it belongs to. It is a
   * bump in the floor at a fixed station parameter, which is a station channel like any other -
   * `floor_elevation` simply rises there. This function exists to make that explicit and to keep the
   * naming honest at call sites.
   */
  fun fjord(stations: StationTable): HeightProfile = glacialTrough(stations)
}
