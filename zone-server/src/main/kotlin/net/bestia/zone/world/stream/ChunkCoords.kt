package net.bestia.zone.world.stream

import net.bestia.bnet.proto.ChunkProto
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.geometry.Vec3L

/**
 * Translation between the ECS's position units and worldgen's chunk and voxel addresses.
 *
 * ### One position unit is one voxel
 *
 * This is the load-bearing assumption of the whole streaming layer, and it is worth stating because the
 * repository is not unanimous about it. [MoveSystem][net.bestia.zone.ecs.movement.MoveSystem] advances a
 * [net.bestia.zone.ecs.movement.Position] by whole units along a path, one per `fraction` rollover, and the
 * AI's `Locomotion` neighbour table steps by one - so a unit behaves as a tile everywhere movement is
 * concerned. Against that, a lone comment on `OutMessageProcessor.UPDATE_RANGE` claims one metre is a
 * hundred units, which is the only place in the repository that says so and which would make that range a
 * ten-kilometre cube.
 *
 * The tile reading is the one that matches the code, and at the default `voxelSize` of one metre it lines
 * positions up with worldgen's voxel indices exactly - no scaling anywhere, and a player's `z` *is* the
 * global voxel index whose zero is sea level. [VOXELS_PER_POSITION_UNIT] exists so that if the other
 * reading ever wins, this is the single place that changes.
 */
object ChunkCoords {

  /**
   * Voxels per ECS position unit. See the class note - one, and deliberately named rather than implied.
   */
  const val VOXELS_PER_POSITION_UNIT = 1L

  /** Which chunk a position falls in. Floor division, so it is correct either side of the origin. */
  fun chunkOf(config: WorldConfig, pos: Vec3L): ChunkPos = ChunkPos(
    x = Math.floorDiv(pos.x * VOXELS_PER_POSITION_UNIT, config.chunkSize.toLong()).toInt(),
    y = Math.floorDiv(pos.y * VOXELS_PER_POSITION_UNIT, config.chunkSize.toLong()).toInt(),
    z = Math.floorDiv(pos.z * VOXELS_PER_POSITION_UNIT, config.chunkHeight.toLong()).toInt()
  )

  /** Global voxel indices of a position. */
  fun voxelOf(pos: Vec3L): Triple<Long, Long, Long> = Triple(
    pos.x * VOXELS_PER_POSITION_UNIT,
    pos.y * VOXELS_PER_POSITION_UNIT,
    pos.z * VOXELS_PER_POSITION_UNIT
  )

  /**
   * Chunk containing a global voxel, plus that voxel's coordinates local to it.
   *
   * Returns `null` if the local coordinates would not fit the chunk, which cannot happen for a finite
   * position but is worth refusing rather than wrapping for a coordinate that arrived from a client.
   */
  fun localise(config: WorldConfig, voxelX: Long, voxelY: Long, voxelZ: Long): Localised? {
    val size = config.chunkSize.toLong()
    val height = config.chunkHeight.toLong()

    val chunk = ChunkPos(
      x = Math.floorDiv(voxelX, size).toInt(),
      y = Math.floorDiv(voxelY, size).toInt(),
      z = Math.floorDiv(voxelZ, height).toInt()
    )

    val localX = Math.floorMod(voxelX, size).toInt()
    val localY = Math.floorMod(voxelY, size).toInt()
    val localZ = Math.floorMod(voxelZ, height).toInt()

    if (localX >= config.chunkSize || localY >= config.chunkSize || localZ >= config.chunkHeight) return null

    return Localised(chunk, localX, localY, localZ)
  }

  data class Localised(val chunk: ChunkPos, val localX: Int, val localY: Int, val localZ: Int)

  /**
   * Index of a voxel inside a chunk's arrays.
   *
   * Must stay identical to `VoxelChunk.index` - the vertical axis is contiguous, and both the chunk
   * payload and the patch format are written against that layout.
   */
  fun voxelIndex(config: WorldConfig, localX: Int, localY: Int, localZ: Int): Int =
    (localY * config.chunkSize + localX) * config.chunkHeight + localZ

  fun toProto(chunk: ChunkPos): ChunkProto.ChunkPos = ChunkProto.ChunkPos.newBuilder()
    .setX(chunk.x)
    .setY(chunk.y)
    .setZ(chunk.z)
    .build()

  fun fromProto(pos: ChunkProto.ChunkPos) = ChunkPos(pos.x, pos.y, pos.z)
}
