package net.bestia.worldgen.civ

/**
 * Why a settlement is where it is.
 *
 * `SettlementStage` already decides this - its five network bonuses *are* the reasons a place becomes a city
 * rather than a village - and until now it threw the answer away, keeping only the number the winning reason
 * contributed. So the world knew that a town sat on a good spot and could no longer say what was good about it,
 * and every stage downstream that wanted to lay a harbour town out differently from a pass town had to
 * re-derive the geography for itself.
 *
 * Stored as an ordinal in [SettlementChannels.FOUNDING_CAUSE], so this list is **append-only**: the same rule
 * [SettlementTier] and `BuildingFunction` follow, and for the same reason - the number is in a station channel
 * in every stored world.
 */
enum class FoundingCause(val label: String) {

  /**
   * Nothing in particular: the ground was simply good.
   *
   * First, so it is the zero a marker with no cause written would read back - which is also why a test asserts
   * that a world holds more than this one value.
   *
   * **Rarer than it sounds**, and worth knowing before reading a census: the harbour, pass and biome-edge
   * fields vary smoothly over most of the map rather than marking a few points, so almost anywhere has *some*
   * bonus and the winner is whichever is largest. A 128 km world came out 15 passes, 11 biome edges, 2
   * confluences and no plain land at all. This is the value for ground no bonus reaches at all.
   */
  LAND("good land"),

  /** Two rivers meet: two valleys' traffic has to cross here anyway. */
  CONFLUENCE("a confluence"),

  /** A river reaches the sea. Where a river's trade changes hands to a ship's. */
  RIVER_MOUTH("a river mouth"),

  /** Sheltered water. Counted apart from the habitability term of the same name - a port with poor land pays. */
  HARBOUR("a harbour"),

  /** The low way through high ground, and so the only way a road and an army can go. */
  PASS("a pass"),

  /** Two biomes within a day's walk, which means two things to trade. */
  BIOME_EDGE("a biome edge")
}
