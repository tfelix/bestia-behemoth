package net.bestia.zone.world

import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig

/**
 * The two directions between this server's records and the generator's own config.
 *
 * Deliberately not one shared class. [WorldConfig] is worldgen's, and worldgen must not learn about JPA or
 * Spring - it is linked into the server today and possibly the client later, so the boundary is the point.
 * Keeping the mapping in one file instead means there is a single place to look when a field is added, rather
 * than a conversion inlined at each call site with one of them forgotten.
 */

/** Birth settings plus a chosen seed, as the generator wants them. */
fun WorldGenConfig.toWorldConfig(seed: Long) = WorldConfig(
  seed = seed,
  widthCells = widthCells,
  heightCells = heightCells,
  baseResolution = Resolution(cellSizeMetres),
  seaLevel = seaLevelMetres,
  chunkSize = chunkSize,
  chunkHeight = chunkHeight,
  voxelSize = voxelSizeMetres
)

/**
 * A stored world, as the generator wants it.
 *
 * This is the authoritative direction. What gets generated comes from the row, never from the configuration
 * file, because the row is what the world's chunks and any edits over them were built against.
 */
fun PersistedWorld.toWorldConfig() = WorldConfig(
  seed = seed,
  widthCells = widthCells,
  heightCells = heightCells,
  baseResolution = Resolution(cellSizeMetres),
  seaLevel = seaLevelMetres,
  chunkSize = chunkSize,
  chunkHeight = chunkHeight,
  voxelSize = voxelSizeMetres
)
