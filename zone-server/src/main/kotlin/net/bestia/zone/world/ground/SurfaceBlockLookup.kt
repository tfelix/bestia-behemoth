package net.bestia.zone.world.ground

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceColumns
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * What block is standing at the top of a voxel column.
 *
 * ### It asks the voxels, not the climate
 *
 * The obvious source is `SurfaceCover.cap(biome, temperature, ...)`, and it has a hole: streets are stamped
 * `COBBLESTONE`, bridges `MASONRY`, a mine collar worked stone, all by the *voxel* pass, and `cap` never sees
 * any of it. That once let a fire cross a paved road. `ChunkMaterializer.surfaceColumns` gives the block
 * actually there, so a road is a firebreak - and unwearable - for free and with no geometry test.
 *
 * ### One cache, because two would be two
 *
 * Fire asks what burns and wear asks what scuffs, and both questions start by asking what the ground is made
 * of. Materialising a column costs up to two slabs, so a second private copy of this cache would double that
 * for nothing. Extracted from `SurfaceBurnableGround`, which held it first.
 *
 * **Never invalidated**, on `ChunkStreamConfig.slabCacheCapacity`'s argument: this is a pure function of the
 * generated world. A player carving terrain could in principle change it, and the consequence is treating a
 * freshly dug pit as whatever used to be on top - not worth an invalidation path for grass and footprints.
 */
@Service
class SurfaceBlockLookup(
  private val worldService: WorldService,
) {

  private val surfaceCache = object : LinkedHashMap<Long, SurfaceColumns>(CACHE_CAPACITY, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<Long, SurfaceColumns>) = size > CACHE_CAPACITY
  }

  /**
   * @return the surface block at this tile, or null before a world is loaded or for an id this build does not
   *   know. `ofOrNull`, not `of`: the throwing variant means "written by another version", which is the right
   *   reaction when decoding a stored chunk and the wrong one on the tick thread inside a grass fire.
   */
  fun blockAt(voxelX: Long, voxelY: Long): BlockType? {
    if (!worldService.isLoaded) return null

    val chunkSize = worldService.config.chunkSize.toLong()

    val chunkX = Math.floorDiv(voxelX, chunkSize).toInt()
    val chunkY = Math.floorDiv(voxelY, chunkSize).toInt()

    val columns = surfaceCache.getOrPut(ColumnKey.of(chunkX, chunkY)) {
      worldService.generated.materializer.surfaceColumns(chunkX, chunkY)
    }

    val block = columns.blockAt(
      Math.floorMod(voxelX, chunkSize).toInt(),
      Math.floorMod(voxelY, chunkSize).toInt()
    )

    return BlockType.ofOrNull(block)
  }

  private companion object {

    /** Chunk columns held at once. A fire spans a handful; a view volume is 121. */
    const val CACHE_CAPACITY = 512
  }
}
