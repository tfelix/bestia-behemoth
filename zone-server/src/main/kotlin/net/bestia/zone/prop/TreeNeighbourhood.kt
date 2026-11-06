package net.bestia.zone.prop

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.PropId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * Where the generator put each tree, and whether a grown one stands near a removed one.
 *
 * Read off the generator rather than the resident entities, because a stump in a wood nobody visits still has
 * to know whether the wood around it stands. Cached per column: one column costs a thousand heightfield
 * evaluations, and a stump asks about up to four of them every time it tries to sprout.
 *
 * Tick thread only, like the divergence registry it asks.
 */
@Service
class TreeNeighbourhood(
  private val worldService: WorldService,
  private val generated: GeneratedPropSource,
  private val kinds: PropKindRegistry,
  private val divergence: WorldObjectDivergenceRegistry
) {

  private val treesByColumn = object : LinkedHashMap<ChunkPos, List<WorldObjectSite>>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ChunkPos, List<WorldObjectSite>>): Boolean {
      return size > CACHED_COLUMNS
    }
  }

  /** The chunk column the tree [propId] stands in. A trunk never leaves its lattice cell, nor a cell its column. */
  fun columnOf(propId: Long): ChunkPos {
    val config = worldService.config
    val cellSize = worldService.generated.params.vegetation.cellSize

    fun columnAlong(cell: Long): Int {
      val voxel = Math.floor((cell + 0.5) * cellSize / config.voxelSize).toLong()
      return Math.floorDiv(voxel, config.chunkSize.toLong()).toInt()
    }

    return ChunkPos(columnAlong(PropId.cellXOf(propId)), columnAlong(PropId.cellYOf(propId)), 0)
  }

  /**
   * Whether a grown tree stands within [radius] position units of the removed tree [propId].
   *
   * Only a grown one: a tree with any divergence row - a stump, a sapling, a young tree - does not count, so a
   * cleared wood grows back from its edge, one ring of trees at a time.
   */
  fun hasGrownTreeNear(propId: Long, radius: Long): Boolean {
    val centre = treesIn(columnOf(propId)).firstOrNull { it.propId == propId }?.position ?: return false
    val chunkSize = worldService.config.chunkSize.toLong()

    for (columnX in Math.floorDiv(centre.x - radius, chunkSize)..Math.floorDiv(centre.x + radius, chunkSize)) {
      for (columnY in Math.floorDiv(centre.y - radius, chunkSize)..Math.floorDiv(centre.y + radius, chunkSize)) {
        val near = treesIn(ChunkPos(columnX.toInt(), columnY.toInt(), 0)).any { tree ->
          val dx = tree.position.x - centre.x
          val dy = tree.position.y - centre.y

          tree.propId != propId && dx * dx + dy * dy <= radius * radius && divergence.of(tree.propId) == null
        }
        if (near) return true
      }
    }

    return false
  }

  private fun treesIn(column: ChunkPos): List<WorldObjectSite> {
    return treesByColumn.getOrPut(column) {
      generated.sitesIn(column).filter { kinds.of(it.kind).regrowth != null }
    }
  }

  private companion object {
    /** About a square kilometre of woods, and some eight megabytes if every column is a dense wood. */
    const val CACHED_COLUMNS = 1024
  }
}
