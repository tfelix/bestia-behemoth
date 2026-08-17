package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.Passability
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.math.abs

/** How big the thing walking is, and how athletic. */
data class AgentProfile(
  /** Voxels of clear space needed above a floor. */
  val height: Int = 2,
  /**
   * How far a step may rise or fall, in voxels.
   *
   * Fractional because floors are: a shallow slope of terrain is a sequence of surfaces a fraction of a
   * voxel apart, and an integer step height would either quantise that to nothing or wave through a rise
   * nearly twice its real size.
   */
  val maxStep: Double = 1.0,
  /**
   * Deepest [net.bestia.worldgen.voxel.Passability.SWIMMABLE] fluid still walkable rather than swum, in
   * voxels.
   *
   * Keyed on the classification rather than on water by name, so a second wadeable fluid needs no new knob.
   * There is deliberately no equivalent for [net.bestia.worldgen.voxel.Passability.BLOCKED]: a wading limit
   * exists because water has a depth you can be partway into, and lava's only correct limit is zero.
   */
  val maxWadeDepth: Double = 1.0
) {
  init {
    require(height >= 1) { "height must be at least 1" }
    require(maxStep >= 0.0) { "maxStep must not be negative" }
    require(maxWadeDepth >= 0.0) { "maxWadeDepth must not be negative" }
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
 * A *span* is one standable place in a column: the height of the surface you stand on, given enough
 * headroom above it. Most columns have exactly one; a column inside a building has one per floor, and one
 * in a cave has one per chamber. Spans are stored in compressed-row form because "most columns have one"
 * is the common case and a list per column would be mostly object headers.
 *
 * Surfaces are heights in voxel units, not voxel indices, because voxels are partially filled: standing on
 * a voxel that is thirty percent full puts your feet at `z + 0.3`. Rounding that away would reintroduce the
 * metre stair-steps occupancy exists to remove, and it would do so specifically in the structure that
 * decides whether terrain is traversable - so gentle slopes would become impassable walls of a fifth of a
 * metre.
 *
 * **Links are not stored.** Two spans in neighbouring columns are connected when their surfaces are within
 * the agent's step height, which is a subtraction - cheaper to evaluate than to look up. Not storing them
 * has a second benefit that matters more: connectivity across a chunk border is computed from the two tiles
 * at query time, so a delta in one chunk never invalidates its neighbours' tiles.
 */
class WalkableTile(
  val chunk: ChunkPos,
  val size: Int,
  val agent: AgentProfile,
  /** CSR offsets into [surfaces], length `size * size + 1`. */
  private val spanStart: IntArray,
  /** Height of each walkable surface in voxel units, ascending within a column. */
  private val surfaces: DoubleArray
) {

  val spanCount get() = surfaces.size

  /** Columns with at least one standable surface. */
  val walkableColumns: Int
    get() = (0 until size * size).count { spanStart[it + 1] > spanStart[it] }

  fun spanCountAt(localX: Int, localY: Int): Int {
    val column = localY * size + localX
    return spanStart[column + 1] - spanStart[column]
  }

  /** Height of the [index]th standable surface in a column, counting from the bottom. */
  fun surfaceAt(localX: Int, localY: Int, index: Int): Double {
    val column = localY * size + localX
    val slot = spanStart[column] + index
    require(slot < spanStart[column + 1]) {
      "column ($localX,$localY) has ${spanCountAt(localX, localY)} spans, asked for $index"
    }
    return surfaces[slot]
  }

  /** Local z of the voxel the [index]th standable surface sits on top of. */
  fun floorAt(localX: Int, localY: Int, index: Int): Int =
    ColumnSummary.voxelOf(surfaceAt(localX, localY, index))

  /** Every standable surface in a column, ascending. */
  fun surfacesAt(localX: Int, localY: Int): DoubleArray {
    val column = localY * size + localX
    return surfaces.copyOfRange(spanStart[column], spanStart[column + 1])
  }

  /** Whether [localZ] is a voxel an agent can stand on top of. */
  fun isWalkable(localX: Int, localY: Int, localZ: Int): Boolean {
    val column = localY * size + localX
    for (slot in spanStart[column] until spanStart[column + 1]) {
      if (ColumnSummary.voxelOf(surfaces[slot]) == localZ) return true
    }
    return false
  }

  /**
   * The surface in a neighbouring column reachable in one step from [fromSurface], or -1.0.
   *
   * Takes the closest candidate rather than the first, so a doorway with a floor above and below it
   * connects to the one actually at foot height.
   */
  fun stepTarget(localX: Int, localY: Int, fromSurface: Double): Double {
    val column = localY * size + localX
    var best = -1.0
    var bestRise = Double.MAX_VALUE

    for (slot in spanStart[column] until spanStart[column + 1]) {
      val rise = abs(surfaces[slot] - fromSurface)
      if (rise <= agent.maxStep && rise < bestRise) {
        bestRise = rise
        best = surfaces[slot]
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
     * A floor is a solid voxel with [AgentProfile.height] voxels of clear space above it. A shallow wadeable
     * fluid counts as clear - an agent wades - but only up to the profile's wading depth, past which the
     * surface is not walkable and pathing has to go round or swim.
     *
     * The part of a partially-filled floor voxel that is *not* solid is not counted as headroom. Ignoring it
     * is conservative in the right direction: an agent that fits is never told it does not.
     *
     * Nothing here names a material. Both questions it asks - can this bear weight, can I move through it -
     * are answered by a declared property on [BlockType], so a new material's behaviour is decided where it
     * is declared rather than by whether somebody remembered to add it to a `when` in here.
     */
    fun of(voxels: VoxelChunk, agent: AgentProfile = AgentProfile()): WalkableTile {
      val size = voxels.size
      val columns = size * size
      val spanStart = IntArray(columns + 1)
      val found = ArrayList<Double>(columns)

      for (localY in 0 until size) {
        for (localX in 0 until size) {
          val column = localY * size + localX
          spanStart[column] = found.size
          val offset = voxels.columnOffset(localX, localY)

          var z = 0
          while (z < voxels.height - 1) {
            val here = BlockType.ofOrNull(voxels.blocks[offset + z].toInt() and 0xFF)
            if (here == null || !here.solid) {
              z++
              continue
            }

            if (hasClearance(voxels, offset, z + 1, agent)) {
              found.add(z + Occupancy.fractionOf(voxels.occupancy[offset + z].toInt() and 0xFF))
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

      return WalkableTile(voxels.chunk, size, agent, spanStart, found.toDoubleArray())
    }

    /** Whether an agent standing at [fromZ] has room, allowing for a shallow fluid to wade through. */
    private fun hasClearance(
      voxels: VoxelChunk,
      offset: Int,
      fromZ: Int,
      agent: AgentProfile
    ): Boolean {
      if (fromZ + agent.height > voxels.height) return false

      // Summed as fractions rather than counted, because the topmost fluid voxel is partly full: an agent
      // whose wading limit is one voxel can stand in 1.0 of water but not 1.3, and counting voxels cannot
      // tell those apart.
      var wade = 0.0
      for (z in fromZ until fromZ + agent.height) {
        val block = BlockType.ofOrNull(voxels.blocks[offset + z].toInt() and 0xFF) ?: return false
        // Exhaustive with no `else`, so a new Passability value is a compile error here rather than a
        // material that silently becomes free headroom. That is what this replaced.
        when (block.passability) {
          Passability.OPEN -> Unit
          Passability.SWIMMABLE -> {
            wade += Occupancy.fractionOf(voxels.occupancy[offset + z].toInt() and 0xFF)
            if (wade > agent.maxWadeDepth) return false
          }
          Passability.BLOCKED -> return false
        }
      }

      return true
    }
  }
}
