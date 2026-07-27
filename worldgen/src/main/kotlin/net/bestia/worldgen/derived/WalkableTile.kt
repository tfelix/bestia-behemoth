package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk

/** How big the thing walking is, and how athletic. */
data class AgentProfile(
  /** Voxels of clear space needed above a floor. */
  val height: Int = 2,
  /** Voxels a step may rise or fall in one move. */
  val maxStep: Int = 1,
  /** Deepest water still walkable rather than swum. */
  val maxWadeDepth: Int = 1
) {
  init {
    require(height >= 1) { "height must be at least 1" }
    require(maxStep >= 0) { "maxStep must not be negative" }
  }
}

/**
 * The walkable surfaces of one chunk: a navmesh tile for a voxel world.
 *
 * Not derived from voxels at request time, and that is the point. A voxelised navmesh rebuild is
 * expensive, and deriving it on demand means doing one every time a player places a block. It is built
 * once, kept, and rebuilt per tile when a delta touches that tile - which is exactly what tiling exists
 * for.
 *
 * A *span* is one standable place in a column: the local z of the block you stand on, given enough
 * headroom above it. Most columns have exactly one; a column inside a building has one per floor, and one
 * in a cave has one per chamber. Spans are stored in compressed-row form because "most columns have one"
 * is the common case and a list per column would be mostly object headers.
 *
 * **Links are not stored.** Two spans in neighbouring columns are connected when their floors are within
 * the agent's step height, which is a comparison of two integers - cheaper to evaluate than to look up.
 * Not storing them has a second benefit that matters more: connectivity across a chunk border is computed
 * from the two tiles at query time, so a delta in one chunk never invalidates its neighbours' tiles.
 */
class WalkableTile(
  val chunk: ChunkPos,
  val size: Int,
  val agent: AgentProfile,
  /** CSR offsets into [floors], length `size * size + 1`. */
  private val spanStart: IntArray,
  /** Local z of each walkable floor, ascending within a column. */
  private val floors: IntArray
) {

  val spanCount get() = floors.size

  /** Columns with at least one standable surface. */
  val walkableColumns: Int
    get() = (0 until size * size).count { spanStart[it + 1] > spanStart[it] }

  fun spanCountAt(localX: Int, localY: Int): Int {
    val column = localY * size + localX
    return spanStart[column + 1] - spanStart[column]
  }

  /** Local z of the [index]th standable floor in a column, counting from the bottom. */
  fun floorAt(localX: Int, localY: Int, index: Int): Int {
    val column = localY * size + localX
    val slot = spanStart[column] + index
    require(slot < spanStart[column + 1]) {
      "column ($localX,$localY) has ${spanCountAt(localX, localY)} spans, asked for $index"
    }
    return floors[slot]
  }

  /** Every standable floor in a column, ascending. */
  fun floorsAt(localX: Int, localY: Int): IntArray {
    val column = localY * size + localX
    return floors.copyOfRange(spanStart[column], spanStart[column + 1])
  }

  fun isWalkable(localX: Int, localY: Int, localZ: Int): Boolean {
    val column = localY * size + localX
    for (slot in spanStart[column] until spanStart[column + 1]) {
      if (floors[slot] == localZ) return true
    }
    return false
  }

  /**
   * The floor in a neighbouring column reachable in one step from [fromFloor], or -1.
   *
   * Takes the closest candidate rather than the first, so a doorway with a floor above and below it
   * connects to the one actually at foot height.
   */
  fun stepTarget(localX: Int, localY: Int, fromFloor: Int): Int {
    val column = localY * size + localX
    var best = -1
    var bestRise = Int.MAX_VALUE

    for (slot in spanStart[column] until spanStart[column + 1]) {
      val rise = kotlin.math.abs(floors[slot] - fromFloor)
      if (rise <= agent.maxStep && rise < bestRise) {
        bestRise = rise
        best = floors[slot]
      }
    }

    return best
  }

  override fun toString() =
    "WalkableTile[$chunk, $spanCount spans over $walkableColumns of ${size * size} columns]"

  companion object {

    /**
     * Finds every standable surface in a chunk.
     *
     * A floor is a solid block with [AgentProfile.height] voxels of clear space above it. Shallow water
     * counts as clear - an agent wades - but only up to the profile's wading depth, past which the surface
     * is not walkable and pathing has to go round or swim.
     */
    fun of(voxels: VoxelChunk, agent: AgentProfile = AgentProfile()): WalkableTile {
      val size = voxels.size
      val columns = size * size
      val spanStart = IntArray(columns + 1)
      val found = ArrayList<Int>(columns)

      for (localY in 0 until size) {
        for (localX in 0 until size) {
          val column = localY * size + localX
          spanStart[column] = found.size
          val offset = voxels.columnOffset(localX, localY)

          var z = 0
          while (z < voxels.height - 1) {
            val here = BlockType.ofOrNull(voxels.blocks[offset + z].toInt() and 0xFF)
            if (here == null || !here.solid || here == BlockType.WATER) {
              z++
              continue
            }

            if (hasClearance(voxels, offset, z + 1, agent)) {
              found.add(z)
              // Skip the clearance we just verified: the blocks inside an agent's own headroom cannot
              // themselves be floors, and re-testing them is the difference between one pass and several.
              z += agent.height
            } else {
              z++
            }
          }
        }
      }
      spanStart[columns] = found.size

      return WalkableTile(voxels.chunk, size, agent, spanStart, found.toIntArray())
    }

    /** Whether an agent standing at [fromZ] has room, allowing for shallow water to wade through. */
    private fun hasClearance(
      voxels: VoxelChunk,
      offset: Int,
      fromZ: Int,
      agent: AgentProfile
    ): Boolean {
      if (fromZ + agent.height > voxels.height) return false

      var water = 0
      for (z in fromZ until fromZ + agent.height) {
        val block = BlockType.ofOrNull(voxels.blocks[offset + z].toInt() and 0xFF) ?: return false
        when {
          block == BlockType.AIR -> Unit
          block == BlockType.WATER -> {
            water++
            if (water > agent.maxWadeDepth) return false
          }
          // Anything else solid is an obstruction; anything else non-solid is passable.
          block.solid -> return false
        }
      }

      return true
    }
  }
}
