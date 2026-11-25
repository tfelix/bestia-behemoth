package net.bestia.zone.casting.sense

/**
 * Where Observation puts what it found on the Scholar's own map. Only from level 3, which is where Observation tells
 * the distance: a pin on a map would give it away otherwise. A port, since the map lives in a higher slice.
 */
fun interface ObservedMarks {

  /** Replaces what the last reading put on the map. */
  fun record(accountId: Long, masterId: Long, observed: List<Observed>)

  /** One mark as the map shows it: where, a short name, and what more the reading told. */
  data class Observed(val east: Double, val north: Double, val name: String, val note: String?)
}
