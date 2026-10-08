package net.bestia.zone.water

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Water the server puts into the world.
 *
 * A tick-thread service like `GroundFireService`: [requestPour] is for a caller with no world scope, such as a chat
 * command on an IO thread, and [drainPours] does the work on the tick.
 */
@Service
class WaterService(
  private val chunkService: ChunkService,
) {

  private class Pour(val x: Long, val y: Long, val z: Long, val radius: Int)

  private val requested = ConcurrentLinkedQueue<Pour>()

  val isIdle: Boolean
    get() = requested.isEmpty()

  /**
   * Asks for a bowl of water around a voxel: the lower half of a sphere, into air only. It is poured on the next
   * tick, so nothing is returned.
   */
  fun requestPour(x: Long, y: Long, z: Long, radius: Int) {
    require(radius in 1..MAX_POUR_RADIUS) { "A pour radius must be 1..$MAX_POUR_RADIUS, was $radius" }
    requested.add(Pour(x, y, z, radius))
  }

  /** @return how many voxels became water */
  fun drainPours(): Int {
    var filled = 0
    while (true) {
      val pour = requested.poll() ?: return filled
      filled += pour(pour)
    }
  }

  private fun pour(pour: Pour): Int {
    val config = chunkService.config
    val radius = pour.radius
    val batches = LinkedHashMap<ChunkPos, MutableSet<Long>>()

    for (dz in -radius..0) {
      for (dy in -radius..radius) {
        for (dx in -radius..radius) {
          if (dx * dx + dy * dy + dz * dz > radius * radius) continue

          val localised = ChunkCoords.localise(config, pour.x + dx, pour.y + dy, pour.z + dz) ?: continue
          val chunk = chunkService.normalise(localised.chunk)
          val index = ChunkCoords.voxelIndex(config, localised.localX, localised.localY, localised.localZ)
          batches.getOrPut(chunk) { HashSet() }.add(VoxelEdit.pack(index, BlockType.WATER, Occupancy.FULL))
        }
      }
    }

    return batches.entries.sumOf { (chunk, edits) -> chunkService.editFluid(chunk, edits.sorted().toLongArray()) }
  }

  companion object {
    /** About eight thousand voxels at the cap: plenty to watch, too little to stall a tick. */
    const val MAX_POUR_RADIUS = 16
  }
}
