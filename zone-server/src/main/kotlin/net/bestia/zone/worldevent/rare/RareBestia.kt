package net.bestia.zone.worldevent.rare

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.world.time.BestiaDateTime
import net.bestia.zone.world.time.DayPart
import net.bestia.zone.world.time.MoonPhase
import net.bestia.zone.world.time.Season

/** One named bestia of `rare_bestia.yml` and the land and sky it comes under. An empty set allows anything. */
data class RareBestia(
  val id: String,
  /** An event-only species from `mob/`. */
  val bestia: String,
  val biomes: Set<Biome> = emptySet(),
  val weather: Set<WeatherKind> = emptySet(),
  val dayParts: Set<DayPart> = emptySet(),
  val moons: Set<MoonPhase> = emptySet(),
  val seasons: Set<Season> = emptySet(),
) {

  /** What townsfolk say about where and when it comes, in the client's dialogs.csv. */
  val hintKey: String get() = "RARE_HINT_" + id.uppercase().replace('-', '_')

  /** Its species' name, in the client's bestias.csv. */
  val nameKey: String get() = "BESTIA_" + bestia.uppercase()

  fun livesOn(biome: Biome): Boolean = biomes.isEmpty() || biome in biomes

  /** The part of its sky that is the same everywhere: the time of day, the moon and the season. */
  fun fitsTime(now: BestiaDateTime): Boolean =
    (dayParts.isEmpty() || now.dayPart in dayParts) &&
      (moons.isEmpty() || now.moonPhase in moons) &&
      (seasons.isEmpty() || now.season in seasons)

  fun fitsSky(kind: WeatherKind, now: BestiaDateTime): Boolean =
    (weather.isEmpty() || kind in weather) && fitsTime(now)
}
