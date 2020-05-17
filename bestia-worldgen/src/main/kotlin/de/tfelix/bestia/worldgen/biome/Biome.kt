package de.tfelix.bestia.worldgen.biome

/**
 * Biome classification is based loosely on the
 * https://en.wikipedia.org/wiki/Holdridge_life_zones
 */
enum class Biome {
  ICE_DESERT,
  DESERT,

  DRY_TUNDRA, // Gravel, thorn bushes
  MOIST_TUNDRA, // Grassland, Moss

  DRY_FORREST,
  MOIST_FORREST,
  RAIN_FORREST,

  MOUNTAIN
}