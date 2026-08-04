package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.NavGraph
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.graph.NavWorldSource
import org.springframework.stereotype.Service

/**
 * Answers [NavWorldSource] from the generated world.
 *
 * Thin, like [ChunkGroundHeight] and [ChunkWalkQuery] beside it: every conversion between the generator's
 * metres and the ECS's position units lives on this side of the seam, so `navigation/` never has to know the
 * exchange rate - see [ChunkCoords]'s class note on why that convention is worth keeping in one place.
 */
@Service
class ChunkNavWorldSource(private val chunkService: ChunkService) : NavWorldSource {

  override val isReady: Boolean get() = chunkService.isReady

  override fun navGraph(): NavGraph = chunkService.navGraphSource()

  /**
   * Horizontal only. The vertical is deliberately left at zero - see [NavWorldSource.place].
   *
   * Sampling the ground here was the first version and it was a real stall, not a missed micro-optimisation:
   * [ChunkService.surfaceElevationAt] computes all 1024 columns of a chunk on a cache miss, nodes are spread
   * one per chunk by construction, and the whole graph is adapted inside a single tick. A few hundred nodes
   * therefore meant a few hundred chunk-height computations back to back on the tick thread, which stalled the
   * zone long enough to fail an integration test's position update.
   */
  override fun place(metresX: Double, metresY: Double): Vec3L {
    val config = chunkService.config
    val x = Math.floor(metresX / config.voxelSize).toLong() / ChunkCoords.VOXELS_PER_POSITION_UNIT
    val y = Math.floor(metresY / config.voxelSize).toLong() / ChunkCoords.VOXELS_PER_POSITION_UNIT

    return Vec3L(x, y, 0L)
  }

  override fun chunkAt(position: Vec3L): ChunkPos? {
    val localised = ChunkCoords.localise(chunkService.config, position.x, position.y, 0) ?: return null
    return chunkService.normalise(localised.chunk)
  }

  override fun onChunkChanged(handler: (ChunkPos) -> Unit) {
    chunkService.onChunkChanged(handler)
  }
}
