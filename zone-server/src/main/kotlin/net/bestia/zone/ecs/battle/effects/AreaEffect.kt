package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import kotlin.math.roundToInt

/**
 * A patch of ground that hurts whatever stands in it, on its own cadence and for its own number of
 * ticks. Lives on a short-lived entity of its own, which [AreaEffectSystem] destroys once the last
 * tick has landed.
 *
 * ### Why a tick count rather than a remaining duration
 *
 * The obvious shape is `remainingSeconds`, decremented alongside a tick accumulator. It makes the
 * number of ticks depend on frame pacing: with a 1.2s interval over 9.6s, real deltas accumulating to
 * 9.599 expire the effect one tick short, and to 9.601 land the last tick with 1.2s still nominally
 * left. Counting ticks instead makes "eight ticks of damage" exactly true however the frames fall,
 * and the duration is simply what those eight ticks take.
 *
 * Not `Dirtyable`: the client learns the patch exists from the entity's
 * [net.bestia.zone.ecs.entity.EntityVisual] and needs none of the numbers below.
 */
data class AreaEffect(
  /** Credited with every tick, so kills and threat land on whoever cast the skill. */
  val casterId: EntityId,

  val skillId: Long,
  val skillLevel: Int,

  /**
   * Half the edge of the affected cube, in tiles: a radius of 1 covers the 3x3 the caster aimed at.
   * See [AreaEffectSystem] for why this is not a distance test.
   */
  /**
   * Half the cube's edge. **Mutable**, because a spreading grass fire resizes the effect covering it rather
   * than replacing it - re-spawning would broadcast an entity appearing every second.
   */
  var radiusTiles: Long,

  val damagePerTick: Int,
  val tickIntervalSeconds: Float,
  var remainingTicks: Int,
  var sinceLastTick: Float = 0f,

  /** Fire on the ground burns its caster too; a consecrated patch should not. */
  val hitsCaster: Boolean = true
) : Component {

  init {
    require(radiusTiles >= 0) { "radiusTiles must be >= 0" }
    require(tickIntervalSeconds > 0f) { "tickIntervalSeconds must be > 0" }
    require(remainingTicks > 0) { "an area effect with no ticks left would never do anything" }
    require(damagePerTick >= 0) { "damagePerTick must be >= 0" }
  }

  companion object {

    /**
     * Builds an effect that lasts [durationSeconds], rounding to whole ticks - 9.6 seconds at 1.2 per
     * tick is eight of them.
     */
    fun lasting(
      casterId: EntityId,
      skillId: Long,
      skillLevel: Int,
      radiusTiles: Long,
      damagePerTick: Int,
      tickIntervalSeconds: Float,
      durationSeconds: Float,
      hitsCaster: Boolean = true
    ): AreaEffect = AreaEffect(
      casterId = casterId,
      skillId = skillId,
      skillLevel = skillLevel,
      radiusTiles = radiusTiles,
      damagePerTick = damagePerTick,
      tickIntervalSeconds = tickIntervalSeconds,
      remainingTicks = (durationSeconds / tickIntervalSeconds).roundToInt(),
      hitsCaster = hitsCaster
    )
  }
}
