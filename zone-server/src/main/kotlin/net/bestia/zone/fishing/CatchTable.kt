package net.bestia.zone.fishing

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.navigation.region.WaterKind
import java.util.Random
import kotlin.math.max
import kotlin.math.pow

/**
 * What bites. The spot and the rod filter, the weather, the hour, bait and helpers weight, the master's catch
 * level cap weights again, and one draw picks.
 */
class CatchTable(
  private val entries: List<Entry>,
  private val overCapHalvingLevels: Int,
) {

  /**
   * One thing that can bite.
   *
   * @property waters the waters it lives in; empty for every water
   * @property biomes the ground the angler must stand on; empty for any
   * @property corruptedOnly whether it bites only off corrupted ground
   * @property weather the only weathers it bites in; empty for any
   * @property weatherBoost how much likelier it bites in [boostWeather]
   * @property night true for night only, false for day only, null for both
   * @property nightBoost how much likelier it bites at night
   */
  data class Entry(
    val itemId: Long,
    val level: Int,
    val weight: Int,
    val waters: Set<WaterKind> = emptySet(),
    val biomes: Set<Biome> = emptySet(),
    val corruptedOnly: Boolean = false,
    val weather: Set<WeatherKind> = emptySet(),
    val boostWeather: Set<WeatherKind> = emptySet(),
    val weatherBoost: Double = 1.0,
    val night: Boolean? = null,
    val nightBoost: Double = 1.0,
    val tags: Set<CatchTag> = emptySet(),
  ) {

    /** Whether it can bite at [spot] at all. What the spot does not know refuses nothing. */
    fun bitesAt(spot: FishingSpot): Boolean {
      return (waters.isEmpty() || spot.water == null || spot.water in waters) &&
          (biomes.isEmpty() || spot.biome == null || spot.biome in biomes) &&
          (!corruptedOnly || spot.corrupted != false) &&
          (weather.isEmpty() || spot.weather == null || spot.weather in weather) &&
          (night == null || spot.isNight == null || night == spot.isNight)
    }

    /** How much likelier the spot's weather and hour make it. */
    fun factorAt(spot: FishingSpot): Double {
      val weatherFactor = if (spot.weather != null && spot.weather in boostWeather) weatherBoost else 1.0
      val nightFactor = if (spot.isNight == true) nightBoost else 1.0

      return weatherFactor * nightFactor
    }
  }

  init {
    require(overCapHalvingLevels > 0) { "over-cap-halving-levels must be positive, got $overCapHalvingLevels" }
    entries.forEach { require(it.weight > 0) { "Catch ${it.itemId} has weight ${it.weight}" } }
  }

  val itemIds: Set<Long> get() = entries.mapTo(HashSet()) { it.itemId }

  /**
   * Null only when nothing here can bite: the rod reaches no entry, or the spot refuses every one.
   *
   * @param favoured how much likelier each tag is, from the bait and a helper; tags multiply
   */
  fun draw(
    maxCatchLevel: Int,
    levelCap: Int,
    random: Random,
    spot: FishingSpot = FishingSpot.UNKNOWN,
    favoured: Map<CatchTag, Double> = emptyMap(),
  ): Entry? {
    val reachable = entries.filter { it.level <= maxCatchLevel && it.bitesAt(spot) }
    if (reachable.isEmpty()) {
      return null
    }

    val weights = reachable.map { weightOf(it, levelCap, spot, favoured) }
    var roll = random.nextDouble() * weights.sum()
    for ((index, entry) in reachable.withIndex()) {
      roll -= weights[index]
      if (roll < 0.0) {
        return entry
      }
    }

    return reachable.last()
  }

  fun weightOf(
    entry: Entry,
    levelCap: Int,
    spot: FishingSpot = FishingSpot.UNKNOWN,
    favoured: Map<CatchTag, Double> = emptyMap(),
  ): Double {
    val levelsOverCap = max(0, entry.level - levelCap)
    val tagFactor = entry.tags.fold(1.0) { factor, tag -> factor * (favoured[tag] ?: 1.0) }

    return entry.weight * entry.factorAt(spot) * tagFactor * 0.5.pow(levelsOverCap / overCapHalvingLevels.toDouble())
  }
}
