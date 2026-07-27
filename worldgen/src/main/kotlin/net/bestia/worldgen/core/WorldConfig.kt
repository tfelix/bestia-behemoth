package net.bestia.worldgen.core

import net.bestia.worldgen.vector.Aabb

/**
 * Immutable description of one world: the seed and the dimensions everything else is derived from.
 *
 * Tuning parameters for individual stages do *not* belong here - biome definitions, culture
 * profiles, business preconditions and profile parameters belong in data files that a designer can
 * change without an engineer. This holds only what the framework itself needs.
 */
data class WorldConfig(

  val seed: Long,

  /** World extent at [baseResolution]. 4096x4096 at 1 km is ~16M cells, which fits in RAM. */
  val widthCells: Int = 4096,
  val heightCells: Int = 4096,

  /** Resolution of the heightfield and everything derived from it at world scale. */
  val baseResolution: Resolution = Resolution.KILOMETRE,

  /** Sea level in metres. Elevations are absolute, not relative to this. */
  val seaLevel: Double = 0.0,

  /** Horizontal voxels per chunk edge. */
  val chunkSize: Int = 32,

  /** Vertical voxels per chunk column. */
  val chunkHeight: Int = 256,

  /** Edge length of one voxel in metres. */
  val voxelSize: Double = 1.0
) {

  init {
    require(widthCells > 0 && heightCells > 0) { "World must have a positive extent" }
    require(chunkSize > 0 && chunkHeight > 0) { "Chunk dimensions must be positive" }
    require(voxelSize > 0.0) { "Voxel size must be positive" }
  }

  /** Horizontal edge length of a chunk in metres. */
  val chunkExtent: Double get() = chunkSize * voxelSize

  val worldRegion: CellRegion
    get() = CellRegion.world(widthCells, heightCells, baseResolution)

  val worldBounds: Aabb get() = worldRegion.toWorld()

  /** World-space bounds of a chunk, before any feature corridor margin is added. */
  fun chunkBounds(chunk: ChunkPos): Aabb {
    val minX = chunk.x * chunkExtent
    val minY = chunk.y * chunkExtent
    return Aabb(minX, minY, minX + chunkExtent, minY + chunkExtent)
  }

  /**
   * Global vertical voxel index of an elevation.
   *
   * Index 0 sits at sea level and indices go negative below it, rather than measuring from an arbitrary
   * world floor. That keeps the mapping independent of how deep the deepest trench in a particular
   * world turned out to be - two worlds with different bathymetry still agree on which voxel index a
   * given elevation is, which matters the moment a cache key or a wire format carries one.
   */
  fun voxelZOf(elevation: Double): Int = Math.floor(elevation / voxelSize).toInt()

  /** Which vertical chunk covers an elevation. Negative below sea level. */
  fun chunkZOf(elevation: Double): Int = Math.floorDiv(voxelZOf(elevation), chunkHeight)

  /** Elevation of the bottom face of global voxel [globalZ]. */
  fun elevationOfVoxel(globalZ: Int): Double = globalZ * voxelSize

  /** Lowest global voxel index inside [chunk]. */
  fun voxelBaseOf(chunk: ChunkPos): Int = chunk.z * chunkHeight

  /** World position of the centre of voxel column ([localX], [localY]) inside [chunk]. */
  fun columnCenter(chunk: ChunkPos, localX: Int, localY: Int): Pair<Double, Double> {
    val x = chunk.x * chunkExtent + (localX + 0.5) * voxelSize
    val y = chunk.y * chunkExtent + (localY + 0.5) * voxelSize
    return x to y
  }
}
