package net.bestia.worldgen.coast

/**
 * What a stretch of coast is, as one of six answers.
 *
 * A category rather than a set of numbers because the thing a player sees is categorical: a beach and a cliff
 * are not two points on a scale of beachiness. The numbers that vary continuously - how wide the strand is, how
 * high the berm stands - ride alongside it on the same station table.
 *
 * Ordered so the ordinal is meaningless and nothing may depend on it. It travels as a station channel, which is
 * a `Double`, and **must be read back with `StationTable.valueAt` and never `sample`** - see [CoastChannels].
 */
enum class ShoreKind {

  /** Sand, and the wide gentle strand that goes with it. The default coast of a sheltered temperate sea. */
  SAND_BEACH,

  /** Cobble and gravel, piled steeper and reaching less far. What an exposed or a cold shore weathers to. */
  SHINGLE_BEACH,

  /** Bare rock at the waterline with barely a strand on it: hard stone the waves have swept clean. */
  ROCKY_SHORE,

  /** No strand at all. The land ends and the rock face goes down, and the bed underneath is what shows. */
  SEA_CLIFF,

  /** Flat, wet and vegetated: a sheltered shore with the fresh water to keep it that way. */
  SALT_MARSH,

  /** Where a river is handing the sea more sediment than the waves can take away. */
  DELTA_FLAT
}
