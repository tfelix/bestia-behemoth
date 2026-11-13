package net.bestia.zone.item.mining

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component

/**
 * The ore a carve would break out, read before it happens. A miner learns up front whether a swing is worth it and
 * whether its ore is too hard for them. Tick thread only, like [ChunkService].
 */
@Component
class OreInBrush(
  private val chunks: ChunkService,
  private val oreYield: OreYield,
  private val items: ItemTemplateRegistry,
) {

  class Ore(val voxel: Vec3L, val stack: OreYield.Stack)

  /** Every ore voxel [brush] removes whole. A voxel it only grazes keeps its ore, see `OreHandOut`. */
  fun scan(brush: CarveBrush): List<Ore> {
    val read = HashMap<ChunkPos, VoxelChunk>()
    val found = ArrayList<Ore>()

    for (x in brush.minVoxelX..brush.maxVoxelX) {
      for (y in brush.minVoxelY..brush.maxVoxelY) {
        for (z in brush.minVoxelZ..brush.maxVoxelZ) {
          if (brush.removedFractionOf(x, y, z) < 1.0) continue
          val voxel = Vec3L(x, y, z.toLong())
          val stack = blockAt(voxel, read)?.let(oreYield::of) ?: continue
          found += Ore(voxel, stack)
        }
      }
    }

    return found
  }

  /** Whether any of [ore] is above [maxResourceLevel]; null means nothing is too hard. */
  fun anyAbove(ore: List<Ore>, maxResourceLevel: Int?): Boolean {
    if (maxResourceLevel == null) return false

    return ore.any { (items.baseLevelOf(it.stack.itemId) ?: 0) > maxResourceLevel }
  }

  private fun blockAt(voxel: Vec3L, read: MutableMap<ChunkPos, VoxelChunk>) = ChunkCoords
    .localise(chunks.config, voxel.x, voxel.y, voxel.z)
    ?.let { local ->
      val chunk = read.getOrPut(chunks.normalise(local.chunk)) { chunks.merged(chunks.normalise(local.chunk)) }
      chunk[local.localX, local.localY, local.localZ]
    }
}
