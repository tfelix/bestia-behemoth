package net.bestia.worldgen.vector

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
   * River channel: a parabolic cut of `depth` below the bed elevation across `width`, wrapped in a
   * floodplain shoulder that eases back up to the surrounding terrain.
   *
   * The shoulder is what keeps a big river from looking like a slot cut into a plain. Its width
   * grows with discharge through the `shoulder` channel, so a headwater stream gets a tight V and a
   * lowland trunk gets a broad flat valley floor.
   */
  fun riverChannel(stations: StationTable): HeightProfile {
    val bed = stations.channel(CHANNEL_BED_ELEVATION)
    val width = stations.channel(CHANNEL_WIDTH)
    val depth = stations.channel(CHANNEL_DEPTH)
    val shoulder = stations.channel(CHANNEL_SHOULDER)

    return HeightProfile { lateral, _, station, base ->
      val halfWidth = station[width] * 0.5
      val d = abs(lateral)
      val bankTop = station[bed]

      if (d <= halfWidth) {
        // Wetted channel: parabolic, deepest at the thalweg.
        val t = if (halfWidth > 0.0) d / halfWidth else 1.0
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
