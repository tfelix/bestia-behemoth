package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.voxel.BlockType

/**
 * A column of generated land with no water anywhere in its chunk: solid ground at [surfaceZ] and air above it.
 *
 * In global voxel coordinates, so a test can pour or carve there without knowing which chunk it falls in.
 */
internal class DryLand(val voxelX: Long, val voxelY: Long, val surfaceZ: Int) {

  companion object {

    private const val DRY_ABOVE_SEA_METRES = 6.0

    /** Searches outward from the world centre, since the edges are forced ocean. */
    fun find(world: GeneratedWorld, chunkService: ChunkService): DryLand {
      val config = world.config
      val size = config.chunkSize
      val height = config.chunkHeight
      val centre = (config.widthMetres / config.chunkExtent).toInt() / 2

      for (step in 0 until 200) {
        val chunkXY = centre + step * 3
        for (local in 4 until size - 4 step 7) {
          val elevation = world.columns.heights(ChunkPos(chunkXY, chunkXY, 0), 0)[local, local]
          if (elevation < DRY_ABOVE_SEA_METRES) continue

          val surfaceZ = config.voxelZOf(elevation)
          val localZ = Math.floorMod(surfaceZ, height)
          if (localZ < 4 || localZ > height - 8) continue

          val chunk = ChunkPos(chunkXY, chunkXY, Math.floorDiv(surfaceZ, height))
          val voxels = chunkService.merged(chunk)
          if (voxels.countOf(BlockType.WATER) > 0) continue

          val ground = BlockType.of(voxels.blocks[voxels.index(local, local, localZ)].toInt() and 0xFF)
          val above = voxels.blocks[voxels.index(local, local, localZ + 1)]
          if (!ground.solid || above != BlockType.AIR.id.toByte()) continue

          return DryLand(chunkXY.toLong() * size + local, chunkXY.toLong() * size + local, surfaceZ)
        }
      }

      error("no dry land column found in the fixture world")
    }
  }
}
