package net.bestia.zone.fishing

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.region.WaterKind

/**
 * Where and when a line is in the water: what decides which entries of the catch table can bite.
 *
 * A null field is one the world could not tell, and a catch that asks for it is not refused for it: a test
 * without a generated world fishes everywhere at once.
 *
 * @property water the water the bobber floats on
 * @property biome the ground the angler stands on: a lake in the tundra is a tundra lake, while the biome under
 *   the bobber is only LAKE
 * @property corrupted whether the angler's ground is corrupted
 */
data class FishingSpot(
  val water: WaterKind?,
  val biome: Biome?,
  val weather: WeatherKind?,
  val isNight: Boolean?,
  val corrupted: Boolean?,
) {

  companion object {
    /** A spot the world knows nothing about. */
    val UNKNOWN = FishingSpot(water = null, biome = null, weather = null, isNight = null, corrupted = null)
  }
}

/** What a catch is, for bait and helpers to favour. */
enum class CatchTag {
  JUNK,
  TREASURE,

  /** Grubbing fish of the muddy bottom, which an earthworm draws. */
  BOTTOM,

  /** Hunters that take smaller fish, which a minnow draws. */
  PREDATOR,

  /** Fish that swim in shoals, which chum draws. */
  SHOAL,

  /** Fish of the dark that follow a light. */
  GLOW,
}

/** Asks the world what [FishingSpot] a line is in, and how strong the country round it is. */
interface FishingGround {

  /** The spot of a line cast from [castFrom] onto [bobber], both in tiles. */
  fun spotAt(castFrom: Vec3L, bobber: Vec3L): FishingSpot

  /** The level band of the wild bestias at [position], in tiles; null when there is no world to ask. */
  fun levelsAt(position: Vec3L): IntRange?

  companion object {
    val NONE = object : FishingGround {
      override fun spotAt(castFrom: Vec3L, bobber: Vec3L) = FishingSpot.UNKNOWN
      override fun levelsAt(position: Vec3L): IntRange? = null
    }
  }
}
