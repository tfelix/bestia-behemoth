package net.bestia.zone.world.ground

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Runtime settings for the shaped marks something leaves as it passes.
 *
 * Runtime rather than world settings, as `GroundWearConfig` is: none of these decides what the terrain is, so
 * all may change on a restart. Stamps are never persisted, so unlike wear there is nothing stored for a retune
 * to disagree with.
 *
 * ### The memory this bounds
 *
 * `ColumnStamps` allocates its arrays up front, so a column costs `maxStampsPerColumn * 12` bytes whether it
 * holds one print or all of them. At the defaults that is 576 B a column and 4.7 MB if every one of
 * [maxColumns] fills - the whole subsystem's footprint, and the reason both numbers are here rather than
 * chosen by whoever walks furthest.
 *
 * @property footprintTtlSeconds Bestia seconds a print lasts. Twenty real minutes at `SPEED_FACTOR` 3, which
 *   is long enough to follow something that has walked out of sight and short enough that a crossroads does
 *   not silt up. The knob a tracking skill's usefulness is set by.
 * @property maxStampsPerColumn prints held for one 32 m column, oldest dropped first. Roughly one and a half
 *   crossings, so a busy column shows the most recent tracks rather than every track.
 * @property maxColumns columns holding stamps at once. Unlike wear this is not tied to what players hold:
 *   creatures leave tracks where nobody is watching, and finding those later is the point of tracking at all.
 * @property announceIntervalSeconds Bestia seconds between putting one column's stamps on the wire. **Load
 *   bearing**: each message carries the column's whole stamp set, so without this a walked column would re-send
 *   all of it on every footfall. One real second, which is behind the walker and not noticed.
 */
@ConfigurationProperties(prefix = "ground-stamps")
data class GroundStampConfig(
  val footprintTtlSeconds: Long = 3_600,
  val maxStampsPerColumn: Int = 48,
  val maxColumns: Int = 8_192,
  val announceIntervalSeconds: Long = 3,
) {

  init {
    require(footprintTtlSeconds > 0) { "footprintTtlSeconds must be positive, was $footprintTtlSeconds" }
    require(maxStampsPerColumn > 0) { "maxStampsPerColumn must be positive, was $maxStampsPerColumn" }
    require(maxColumns > 0) { "maxColumns must be positive, was $maxColumns" }
    require(announceIntervalSeconds >= 0) {
      "announceIntervalSeconds cannot be negative, was $announceIntervalSeconds"
    }
  }
}
