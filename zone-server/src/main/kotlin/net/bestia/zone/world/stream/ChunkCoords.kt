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

  /**
   * The `z` an entity standing on ground of a given elevation should have.
   *
   * ### Why this rounds rather than taking the voxel above
   *
   * It used to be `floor(elevation) + 1` - "the first voxel clear of the surface" - and that reasoning holds
   * only while the surface is at a voxel boundary. It is not: `ChunkHeightSampler` produces a continuous height
   * and the occupancy field records the fraction, so the client's mesher reconstructs a surface at, say, 409.3 m.
   * The client renders `z` directly as its own Y, so `floor + 1` put the player at 410.0 - **floating 0.7 m, and
   * always floating**, because the error was one-sided by construction.
   *
   * Rounding halves the worst case and, more usefully, makes it symmetric: a player is now at most half a voxel
   * out and as likely to be low as high.
   *
   * ### The residual error is the wire format, not this function
   *
   * Half a voxel is as close as an integer `z` can get to a sub-voxel surface. `Vec3L` is longs, the protobuf
   * `Vec3` is `int64`, and the master's position column is a long - so the terrain has finer vertical detail than
   * any position can express. Closing the remaining half metre means giving position a fractional vertical
   * component, which is a change to the ECS, the schema and the wire format together.
   *
   * @param elevation ground elevation in metres, as the heightfield reports it
   * @return position units, clamped so it is never below the waterline - a submarine column reports its seabed,
   *   which in the ocean margin is hundreds of metres down, so honouring it literally drowns the player on the
   *   bottom instead of floating them on top
   */
  fun standingZ(config: WorldConfig, elevation: Double): Long {
    val standing = maxOf(elevation, config.seaLevel)
    return Math.round(standing / config.voxelSize) / VOXELS_PER_POSITION_UNIT
  }

  /**
   * The vertical chunks the sea surface straddles, so a subscriber can be offered the ones with the sea in them.
   *
   * ### Not `chunkZOf(seaLevel)`, which is wrong by a slab
   *
   * Water fills *up to* sea level, so the topmost water voxel is `ceil(seaLevel / voxelSize) - 1` -
   * `ChunkMaterializer`'s own convention. At the default sea level of zero that is voxel **-1**, which lives in
   * chunk -1, whereas `chunkZOf(0.0)` names chunk **0**. Chunk 0 over open ocean is pure air: it encodes to
   * twelve bytes, the client meshes it to nothing, and the sea is invisible. A player standing on open water got
   * an empty screen and a log full of 12-byte chunks.
   *
   * ### Both slabs, because a surface is an interface rather than a voxel
   *
   * Surface nets needs the full voxels below the interface *and* the empty one above it, or there is no sign
   * change to find. At sea level those two fall either side of a chunk boundary, so sending one without the other
   * draws nothing however correct the one you sent is.
   *
   * Collapses to a single slab whenever sea level is not on a chunk boundary, which is the usual case for any
   * sea level that is not a multiple of the slab height.
   */
  fun seaSurfaceSlabs(config: WorldConfig): List<Int> {
    val topWater = Math.ceil(config.seaLevel / config.voxelSize).toInt() - 1

    return listOf(topWater, topWater + 1)
      .map { Math.floorDiv(it, config.chunkHeight) }
      .distinct()
  }

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
