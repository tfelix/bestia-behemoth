package net.bestia.zone.world.ground

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Runtime settings for ground wearing down under traffic.
 *
 * Runtime rather than world settings, in `ChunkStreamConfig`'s sense: none of these decides what the terrain
 * is, so all may change on a restart with no consequence. What is stored is a *level*, not a rate, so retuning
 * [fadeSeconds] reshapes how the paths that already exist fade rather than invalidating them.
 *
 * @property fadeSeconds Bestia seconds for a fully worn cell to close over completely, untouched. The knob
 *   that will be retuned most, which is why it is a duration rather than a rate - "a path lasts three days" is
 *   a sentence somebody can hold an opinion about. Three Bestia days is about a real one at `SPEED_FACTOR` 3.
 * @property stepWeight added per footfall, out of the 255 a cell holds. Set against [visibleThreshold] this
 *   decides how many passes it takes before a route shows at all, independently of how fast it fades.
 * @property neighbourWeight added to the four cells beside the one stepped on. Without this an eight-connected
 *   walk leaves a one metre staircase, which reads as a scratch rather than as a path.
 * @property visibleThreshold the level below which a cell is not sent to clients at all.
 *   **Load bearing rather than cosmetic**: creatures wandering at random spread their wear thinly over a wide
 *   area, and this is what keeps that below notice so only genuinely repeated routes ever show. Raise it if
 *   idling players grow faint haloes; do not reach for an exclusion list first.
 * @property flushIntervalSeconds how often dirty columns are written. Wear is touched far too often to persist
 *   per step the way a scar is, so writes are coalesced and a crash costs a few minutes of footfalls.
 * @property maxResidentColumns worn columns held in memory at once. Bounds the footprint on a busy shard; a
 *   column evicted while still worn is written out first and read back when somebody returns to it.
 */
@ConfigurationProperties(prefix = "ground-wear")
data class GroundWearConfig(
  override val fadeSeconds: Long = 259_200,
  val stepWeight: Int = 6,
  val neighbourWeight: Int = 2,
  override val visibleThreshold: Int = 32,
  override val flushIntervalSeconds: Float = 120f,
  override val maxResidentColumns: Int = 8_192,
) : GroundLevelConfig {

  init {
    require(fadeSeconds > 0) { "fadeSeconds must be positive, was $fadeSeconds" }
    require(stepWeight > 0) { "stepWeight must be positive, was $stepWeight" }
    require(neighbourWeight >= 0) { "neighbourWeight cannot be negative, was $neighbourWeight" }
    require(visibleThreshold in 0..255) { "visibleThreshold must fit a level, was $visibleThreshold" }
    require(maxResidentColumns > 0) { "maxResidentColumns must be positive, was $maxResidentColumns" }
  }
}
