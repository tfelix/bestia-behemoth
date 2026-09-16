package net.bestia.zone.ai.knowledge

/**
 * How long ago something was, as a person would say it rather than as a number.
 *
 * A bare year is the reason history lines read like a chronicle: "in 412" tells a player nothing unless
 * they happen to know what year it is now. The speaker knows, so they can say both - and which one
 * carries the meaning depends on the line.
 *
 * `SettlementLoreService` deliberately hands on a year and a kind rather than a rendered "long ago",
 * on the grounds that how recent something feels is a decision for whoever is speaking. This is that
 * decision, taken one layer up where the speaker is.
 *
 * The bands are here and their key names are repeated in `dialogue.yml`, which is what lets the build
 * check the client has a row for each. [HistoryLineCatalogue] refuses a boot where the two disagree.
 */
enum class Era(private val withinYears: Int?) {

  /** Inside a few years. Somebody was there. */
  RECENT(20),

  /** Old enough to be a story, recent enough that somebody's parents saw it. */
  LIVING(80),

  /** Two or three generations back. Grandparents and their grandparents. */
  GENERATIONS(200),

  /** Beyond anybody's family memory, but still a thing with a date on it. */
  DISTANT(500),

  /** So far back that the year is a scholar's claim rather than a memory. */
  ANCIENT(null);

  /** The translation key this band renders through. */
  val key: String get() = KEY_PREFIX + name

  companion object {

    const val KEY_PREFIX = "ERA_"

    /**
     * The band [yearsAgo] falls in.
     *
     * Clamped at zero, because an event dated after the present is a generator bug rather than a
     * prophecy, and the band it lands in should not depend on which.
     */
    fun of(yearsAgo: Int): Era {
      val ago = yearsAgo.coerceAtLeast(0)

      return entries.first { it.withinYears == null || ago <= it.withinYears }
    }
  }
}
