package net.bestia.zone.environment.time

import net.bestia.worldgen.climate.SeasonalPrecipitation
import net.bestia.worldgen.climate.Seasons

/**
 * The four Bestia seasons. Each lasts exactly one Bestia month ([BestiaDateTime.DAYS_PER_MONTH] Bestia-days).
 *
 * ### Why the order changed
 *
 * https://docs.bestia-game.net/docs/mechanics/environment/#in-game-time *lists* the seasons summer, winter,
 * fall, spring, and this enum used to take that listing as the calendar - `ofMonth` was `entries[month - 1]`.
 * That is not a rotation of a year, it is a reshuffle: it puts summer next to winter and autumn next to
 * spring. Read as an orbital order it needs the planet to reverse direction twice a year, and the generator's
 * seasonal model is a single sine ([Seasons.northernWarming]) which cannot express that at any tuning.
 *
 * Nothing outside this package consumed either the enum or the clock when this was corrected, so the change
 * cost nothing then and would have cost a content pass later. **The docs follow this file, not the reverse.**
 *
 * Never use [Enum.ordinal] for anything: [northernQuarter] is the index that means something, and the two
 * agreeing today is a coincidence worth not depending on.
 */
enum class Season {
  SPRING,
  SUMMER,
  FALL,
  WINTER;

  /**
   * Index of this season's quarter in [SeasonalPrecipitation.LAYERS], as a **northern-hemisphere** label.
   *
   * The generator's four precipitation layers are named for a phase of the orbit rather than for the weather
   * at any given cell, so quarter 1 is `PRECIPITATION_SUMMER` everywhere - and south of the equator that
   * quarter is the local winter. [opposite] is the flip, and [at] is the only place it should be applied to a
   * label a player sees.
   */
  val northernQuarter: Int get() = ordinal

  /** The season half a year away: what the other hemisphere is having right now. */
  val opposite: Season
    get() = when (this) {
      SPRING -> FALL
      SUMMER -> WINTER
      FALL -> SPRING
      WINTER -> SUMMER
    }

  companion object {

    /**
     * The season active during the given 1-indexed Bestia [month] (1..[BestiaDateTime.MONTHS_PER_YEAR]).
     *
     * Spelled out rather than indexed off [Enum.entries], because an index into the declaration order is
     * exactly the conflation this enum's KDoc exists to record.
     */
    fun ofMonth(month: Int): Season = when (month) {
      1 -> SPRING
      2 -> SUMMER
      3 -> FALL
      4 -> WINTER
      else -> throw IllegalArgumentException(
        "month must be in 1..${BestiaDateTime.MONTHS_PER_YEAR}, was $month"
      )
    }

    /**
     * The season being experienced at a place and a time.
     *
     * @param yearProgress fraction of the Bestia year elapsed, from [BestiaDateTime.yearProgress]
     * @param northwards fraction from the world's south edge to its north edge, i.e. `worldY / heightMetres`
     */
    fun at(yearProgress: Double, northwards: Double): Season {
      val northern = entries[Seasons.quarterOf(yearProgress)]
      return if (Seasons.hemisphereSign(northwards) >= 0.0) northern else northern.opposite
    }
  }
}
