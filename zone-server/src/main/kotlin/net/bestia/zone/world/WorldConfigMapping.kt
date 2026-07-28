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
  voxelSize = voxelSizeMetres,
  wrapX = wrapX,
  wrapY = wrapY
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
  voxelSize = voxelSizeMetres,
  wrapX = wrapX,
  wrapY = wrapY
)

/**
 * Which birth settings this world was *not* born with, one readable line each.
 *
 * The counterpart to the two mappings above, and it lives here for the same reason: adding a field to one and
 * not the others is then a visible omission in a single file rather than an invisible one across three.
 *
 * A named difference beats a hash for this. [net.bestia.worldgen.core.WorldConfig.shapeVersion] can only say
 * *that* two worlds differ, which is no help when what you need to know is whether you are about to throw one
 * away over a sea level you did not mean to change.
 *
 * The world's *name* is deliberately not compared: `findFirstByOrderByIdAsc` never looks at it, so renaming
 * one is cosmetic and should not read as a request for different terrain.
 */
fun PersistedWorld.driftFrom(settings: WorldGenConfig): List<String> = buildList {
  fun compare(setting: String, stored: Any?, configured: Any?) {
    if (stored != configured) add("$setting: $stored -> $configured")
  }

  // Only when one is set. An unset seed was drawn at random and written down, so comparing it against `null`
  // would report every world as drifted from the moment it was created. Changing an *explicit* seed, on the
  // other hand, is about as clear a request for a different world as there is.
  settings.seed?.let { compare("seed", seed, it) }

  compare("width-cells", widthCells, settings.widthCells)
  compare("height-cells", heightCells, settings.heightCells)
  compare("cell-size-metres", cellSizeMetres, settings.cellSizeMetres)
  compare("chunk-size", chunkSize, settings.chunkSize)
  compare("chunk-height", chunkHeight, settings.chunkHeight)
  compare("voxel-size-metres", voxelSizeMetres, settings.voxelSizeMetres)
  compare("sea-level-metres", seaLevelMetres, settings.seaLevelMetres)
  compare("wrap-x", wrapX, settings.wrapX)
  compare("wrap-y", wrapY, settings.wrapY)
}
