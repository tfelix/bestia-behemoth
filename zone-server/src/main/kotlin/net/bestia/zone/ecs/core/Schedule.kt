package net.bestia.zone.ecs.core

/**
 * Declares how often a system runs. This is how a system gets a "dedicated
 * execution time": most run [EveryTick], while expensive ones can run e.g.
 * `EverySeconds(180f)` (once every three minutes) or [EveryTicks].
 */
sealed interface Schedule {
  /** Runs on every simulation tick. */
  object EveryTick : Schedule

  /** Runs once every [n] ticks. */
  data class EveryTicks(val n: Int) : Schedule {
    init { require(n >= 1) { "EveryTicks requires n >= 1" } }
  }

  /** Runs once roughly every [seconds] of simulated time. */
  data class EverySeconds(val seconds: Float) : Schedule {
    init { require(seconds > 0f) { "EverySeconds requires seconds > 0" } }
  }
}
