package de.tfelix.bestia.worldgen.biome

/**
 * Biome classification is based loosely on the
 * https://en.wikipedia.org/wiki/Holdridge_life_zones
 */
enum class Biomes {
  ICE_DESERT,

  DRY_TUNDRA,
  MOIST_TUNDRA,
  WET_TUNDRA,
  RAIN_TUNDRA,

  DESERT_1,
  DRY_SCRUB,
  MOIST_FOREST_1,
  WET_FOREST_1,
  RAIN_FOREST_1,

  DESERT_SCRUB,
  STEPPE,

  DESERT_2,
  THORN_STEPPE,
  DRY_FOREST,
  MOIST_FOREST_2,
  WET_FOREST_2,
  RAIN_FOREST_2,

  DESERT_SCRUB_2,
  THORN_WOODLAND,
  VERY_DRY_FOREST
}