package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component

/**
 * How worn down a master or an owned bestia is by low [Stamina], kept by [StaminaExhaustionSystem]. Only an entity
 * below [StaminaBand.TIRED]'s threshold carries one: a rested entity has none, so the hundreds of rested players and
 * every wild mob pay nothing for it.
 *
 * Server bookkeeping, not synced: the client sees the effects (a slower [net.bestia.zone.movement.ecs.Speed], a
 * still health bar, the drain) and the chat line on entering each band, not this.
 */
class Exhaustion(
  var band: StaminaBand,
  /** Share of the walk speed left, in percent; folded in by `StatusValueRecalcSystem`. */
  var speedPercent: Int,
) : Component {

  /** Seconds spent [draining] since the last drain, so leaving the band and coming back starts the clock over. */
  var drainSeconds: Float = 0f

  /** Below 10 % stamina neither health nor mana regenerates. */
  val regenBlocked: Boolean get() = band >= StaminaBand.EXHAUSTED

  /** Below 5 % stamina the body is spending health to keep going. */
  val draining: Boolean get() = band == StaminaBand.COLLAPSING
}

/**
 * The stamina bands the docs give effects to, worst last, so `>=` reads as "at least this worn down". Each one's
 * [belowPercent] is the stamina share an entity has to fall under to enter it.
 */
enum class StaminaBand(val belowPercent: Int) {
  RESTED(Int.MAX_VALUE),
  TIRED(30),
  EXHAUSTED(10),
  COLLAPSING(5);

  companion object {
    fun of(staminaPercent: Int): StaminaBand {
      return when {
        staminaPercent < COLLAPSING.belowPercent -> COLLAPSING
        staminaPercent < EXHAUSTED.belowPercent -> EXHAUSTED
        staminaPercent < TIRED.belowPercent -> TIRED
        else -> RESTED
      }
    }
  }
}
