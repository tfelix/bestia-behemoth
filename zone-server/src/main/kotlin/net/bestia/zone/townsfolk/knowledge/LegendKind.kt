package net.bestia.zone.townsfolk.knowledge

/** What an old saying or a family story is about. Each kind has its phrasings in `dialogue.yml`. */
enum class LegendKind {

  /** Valuables hidden in a cave by somebody who never came back. */
  HOARD,

  /** A world boss that withdrew or vanished, and still lives. */
  LAIR,

  /** A world boss bound under a seal. */
  SEAL,

  /** The field a world boss was slain on. */
  SLAYING,

  /** A relic that was lost on a battlefield and never found. */
  LOST_RELIC,

  /** A family's claim to come down from an old ruling house. See [LineageClaims]. */
  LINEAGE;

  val key: String get() = KEY_PREFIX + name

  companion object {
    const val KEY_PREFIX = "LEGEND_"
  }
}
