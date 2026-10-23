package net.bestia.zone.travel

/** Why a [Journey] ended before its destination. */
enum class JourneyFailure {
  /** Every way the traveller knows of is blocked. */
  NO_ROUTE,

  /** It made no headway for too long: walled in, in a dead end, or facing what it cannot cross. */
  STUCK,

  DIED
}
