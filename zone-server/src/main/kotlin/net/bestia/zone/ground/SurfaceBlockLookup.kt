package net.bestia.zone.ground

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceColumns
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkWorkers
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
 *
 * A miss is built on a [ChunkWorkers] thread, since materialising the top slabs is milliseconds, and answers
 * null until it arrives: one footprint or one spark of fire on a column nobody looked at yet is not worth a
 * stalled tick.
 */
@Service
class SurfaceBlockLookup(
  private val worldService: WorldService,
  private val workers: ChunkWorkers,
) {

  private val surfaceCache = object : LinkedHashMap<Long, SurfaceColumns>(CACHE_CAPACITY, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<Long, SurfaceColumns>) = size > CACHE_CAPACITY
  }

  private val building = HashSet<Long>()

  /**
   * @return the surface block at this tile, or null before a world is loaded, while the column is being built,
   *   or for an id this build does not know. `ofOrNull`, not `of`: the throwing variant means "written by
   *   another version", which is the right reaction when decoding a stored chunk and the wrong one on the tick
   *   thread inside a grass fire.
   */
  fun blockAt(voxelX: Long, voxelY: Long): BlockType? {
    if (!worldService.isLoaded) return null

    val chunkSize = worldService.config.chunkSize.toLong()

    val chunkX = Math.floorDiv(voxelX, chunkSize).toInt()
    val chunkY = Math.floorDiv(voxelY, chunkSize).toInt()

    val columns = columnsOf(chunkX, chunkY) ?: return null

    val block = columns.blockAt(
      Math.floorMod(voxelX, chunkSize).toInt(),
      Math.floorMod(voxelY, chunkSize).toInt()
    )

    return BlockType.ofOrNull(block)
  }

  private fun columnsOf(chunkX: Int, chunkY: Int): SurfaceColumns? {
    val key = ColumnKey.of(chunkX, chunkY)
    surfaceCache[key]?.let { return it }

    if (building.add(key)) {
      val materializer = worldService.generated.materializer
      workers.submit(
        work = { materializer.surfaceColumns(chunkX, chunkY) },
        deliver = { result ->
          building.remove(key)
          result.onSuccess { surfaceCache[key] = it }
        }
      )
    }

    return surfaceCache[key]
  }

  private companion object {

    /** Chunk columns held at once. A fire spans a handful; a view volume is 121. */
    const val CACHE_CAPACITY = 512
  }
}
