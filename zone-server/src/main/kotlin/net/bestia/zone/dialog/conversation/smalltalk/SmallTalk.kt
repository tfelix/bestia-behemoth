package net.bestia.zone.dialog.conversation.smalltalk

import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.place.RegionKind
import net.bestia.worldgen.pop.Kinship
import net.bestia.zone.environment.time.Season

/**
 * One mundane thing a townsperson might say, and what has to be true before they say it.
 *
 * Every gate left empty is a gate that does not apply, and the ones that do apply are ANDed while the
 * values inside one are ORed. So a line naming two occupations and one season is "either of these
 * trades, in that season" - which is the only combination the pool has needed, and a small enough
 * language that a line's condition is legible in the yml without a parser in the reader's head.
 */
data class SmallTalk(
  val key: String,
  val occupations: Set<String> = emptySet(),
  val kinship: Set<Kinship> = emptySet(),
  val terrain: Set<RegionKind> = emptySet(),
  val season: Set<Season> = emptySet(),
  val weather: Set<WeatherKind> = emptySet(),
  val minAge: Int? = null,
  val maxAge: Int? = null,
  val walled: Boolean? = null,
  val sacked: Boolean? = null,
  val minWealth: Double? = null,
  val maxWealth: Double? = null,
) {

  /** The row a player clicks. The reply is [key] itself, so one line is one pair of translations. */
  val askKey: String
    get() {
      return key + ASK_SUFFIX
    }

  /**
   * Whether the only thing this line asks about its speaker is their trade.
   *
   * The test for a fallback, and every gate but [occupations] has to count towards it. A line gated on
   * old age is not something a trade can rely on having, however universal it looks for naming no
   * terrain - which is what the catalogue's boot check is looking for.
   */
  val isUnconditional: Boolean
    get() {
      return kinship.isEmpty() && terrain.isEmpty() && season.isEmpty() && weather.isEmpty() &&
        minAge == null && maxAge == null &&
        walled == null && sacked == null && minWealth == null && maxWealth == null
    }

  fun holdsFor(circumstance: Circumstance): Boolean {
    return matches(occupations, circumstance.occupation) &&
      matches(kinship, circumstance.kinship) &&
      matches(terrain, circumstance.terrain) &&
      matches(season, circumstance.season) &&
      matches(weather, circumstance.weather) &&
      within(circumstance.age, minAge, maxAge) &&
      within(circumstance.wealth, minWealth, maxWealth) &&
      (walled == null || walled == circumstance.walled) &&
      (sacked == null || sacked == circumstance.sacked)
  }

  private fun <T> matches(allowed: Set<T>, actual: T): Boolean {
    return allowed.isEmpty() || actual in allowed
  }

  private fun <T : Comparable<T>> within(actual: T, from: T?, to: T?): Boolean {
    return (from == null || actual >= from) && (to == null || actual <= to)
  }

  companion object {
    const val ASK_SUFFIX = "_ASK"
  }
}
