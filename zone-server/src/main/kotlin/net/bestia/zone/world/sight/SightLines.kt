package net.bestia.zone.world.sight

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.SightMask
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component

/**
 * Whether one point of the world can see another, through terrain and player structures.
 *
 * Reads only sight masks `DerivedStore` already built, out of the derived-rebuild budget and mostly on chunk workers
 * (see `ChunkService.rebuildDerived`); a chunk without one counts as clear, so this never builds anything and costs
 * the tick a short walk over bits. Owned by the tick thread, like the store it reads.
 */
@Component
class SightLines(
  private val chunks: ChunkService,
) {

  private val masks = SightMasks { x, y, z -> maskOf(x, y, z) }

  private fun maskOf(x: Int, y: Int, z: Int): SightMask? {
    return chunks.derived().sightIfBuilt(ChunkPos(x, y, z))
  }

  /** Positions in world units, the voxel each entity stands in. */
  fun isClear(ax: Long, ay: Long, az: Long, bx: Long, by: Long, bz: Long): Boolean {
    if (!chunks.isReady) return true

    val config = chunks.config

    return SightRay.isClear(ax, ay, az, bx, by, bz, config.chunkSize, config.chunkHeight, masks)
  }
}
