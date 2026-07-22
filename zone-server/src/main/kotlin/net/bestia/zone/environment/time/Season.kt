package net.bestia.zone.environment.time

/**
 * The four Bestia seasons. Each lasts exactly one Bestia month (30 Bestia-days). Order
 * matches the sequence listed at
 * https://docs.bestia-game.net/docs/mechanics/environment/#in-game-time (summer, winter,
 * fall, spring) rather than the real-world spring-first ordering.
 */
enum class Season {
  SUMMER,
  WINTER,
  FALL,
  SPRING;

  companion object {
    private val ORDERED = entries.toTypedArray()

    /** The season active during the given 1-indexed Bestia [month] (1..4). */
    fun ofMonth(month: Int): Season {
      require(month in 1..ORDERED.size) { "month must be in 1..${ORDERED.size}, was $month" }
      return ORDERED[month - 1]
    }
  }
}
