package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.Passability
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

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
   *
   * The default of one voxel is not the running game's rule and no longer pretends to be. `zone-server`
   * always builds through [forMaxSlope] from a configured angle, so this is what a test or a caller with no
   * opinion gets - one, because that is the angle at which a rise equals its run.
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
  val maxWadeDepth: Double = 1.0,
  /**
   * How far a step onto or off a player-built surface may rise or fall, in voxels.
   *
   * Lower than [maxStep], which is a slope rule for natural ground: on a built floor it would let a walker climb
   * onto a table, and stairs would not need to be stairs.
   */
  val maxStructureStep: Double = 0.5
) {
  init {
    require(height >= 1) { "height must be at least 1" }
    require(maxStep >= 0.0) { "maxStep must not be negative" }
    require(maxStructureStep >= 0.0) { "maxStructureStep must not be negative" }
    require(maxWadeDepth >= 0.0) { "maxWadeDepth must not be negative" }
  }

  companion object {
    /**
     * An [AgentProfile] whose [maxStep] is derived from a maximum walkable slope rather than stated directly
     * in voxels, since a slope in degrees is what a config file or a game designer actually thinks in.
     *
     * `maxStep` and `voxelSize` are two independent numbers that happen to agree exactly at the 45-degree
     * default, which is worth pinning in a test (see `WalkableSlopeTest`) precisely because nothing in the
     * types enforces it. This factory is the one place the `tan` relationship is computed, so a caller never
     * has to re-derive a step height from a slope by hand.
     */
    fun forMaxSlope(
      voxelSize: Double,
      degrees: Double,
      height: Int = 2,
      maxWadeDepth: Double = 1.0,
      maxStructureStep: Double = 0.5
    ): AgentProfile {
      require(degrees > 0.0 && degrees < 90.0) { "degrees must be strictly between 0 and 90, was $degrees" }

      return AgentProfile(
        height = height,
        maxStep = voxelSize * tan(Math.toRadians(degrees)),
        maxWadeDepth = maxWadeDepth,
        maxStructureStep = maxStructureStep
      )
    }
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
 *
 * Player-built pieces come in as [StructureCells] beside the voxels. A span standing on a built cell is a
 * *structure* span, held to [AgentProfile.maxStructureStep]. Every span also keeps the walls on the borders of its
 * column that its walker's body would hit, as [Edges] bits.
 */
class WalkableTile(
  val chunk: ChunkPos,
  val size: Int,
  val agent: AgentProfile,
  /** CSR offsets into [surfaces], length `size * size + 1`. */
  private val spanStart: IntArray,
  /** Height of each walkable surface in voxel units, ascending within a column. */
  private val surfaces: DoubleArray,
  /** Per span: the [Edges] its walker cannot leave through, and [STRUCTURE_SPAN]. */
  private val spanFlags: ByteArray = ByteArray(surfaces.size)
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
    return spanAt(localX, localY, localZ) != NO_SPAN
  }

  /** The span standing on voxel [localZ] of a column, or [NO_SPAN]. Spans are numbered across the whole tile. */
  fun spanAt(localX: Int, localY: Int, localZ: Int): Int {
    val column = localY * size + localX
    for (slot in spanStart[column] until spanStart[column + 1]) {
      if (ColumnSummary.voxelOf(surfaces[slot]) == localZ) return slot
    }
    return NO_SPAN
  }

  fun surfaceOf(span: Int): Double {
    return surfaces[span]
  }

  fun isStructure(span: Int): Boolean {
    return spanFlags[span].toInt() and STRUCTURE_SPAN != 0
  }

  /** The [Edges] a walker standing on [span] cannot step through. */
  fun wallsOf(span: Int): Int {
    return spanFlags[span].toInt() and Edges.ALL
  }

  /**
   * The surface in a neighbouring column reachable in one step from [fromSurface], or -1.0.
   *
   * Takes the closest candidate rather than the first, so a doorway with a floor above and below it
   * connects to the one actually at foot height.
   */
  fun stepTarget(localX: Int, localY: Int, fromSurface: Double, fromStructure: Boolean = false): Double {
    val span = stepTargetSpan(localX, localY, fromSurface, fromStructure)
    if (span == NO_SPAN) return -1.0

    return surfaces[span]
  }

  /** As [stepTarget], but the span rather than its height. */
  fun stepTargetSpan(localX: Int, localY: Int, fromSurface: Double, fromStructure: Boolean): Int {
    val column = localY * size + localX
    var best = NO_SPAN
    var bestRise = Double.MAX_VALUE

    for (slot in spanStart[column] until spanStart[column + 1]) {
      val rise = abs(surfaces[slot] - fromSurface)
      if (rise <= maxStepBetween(fromStructure, isStructure(slot)) && rise < bestRise) {
        bestRise = rise
        best = slot
      }
    }

    return best
  }

  private fun maxStepBetween(fromStructure: Boolean, toStructure: Boolean): Double {
    if (!fromStructure && !toStructure) return agent.maxStep

    // A fill byte cannot hold exactly half a voxel, so a half step is one quantum taller than half.
    return min(agent.maxStep, agent.maxStructureStep + FILL_QUANTUM)
  }

  override fun toString() =
    "WalkableTile[$chunk, $spanCount spans over $walkableColumns of ${size * size} columns]"

  companion object {

    const val NO_SPAN = -1

    /** Set in a span's flags when it stands on a built cell rather than on terrain. */
    private const val STRUCTURE_SPAN = 0x10

    private const val FILL_QUANTUM = 1.0 / Occupancy.FULL

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
     *
     * A filled cell of [cells] is a floor and no headroom, like a solid voxel; it wins over the voxel it sits in.
     *
     * [above] is the slab on top of this one, and it holds the headroom of every floor in this slab's last
     * [AgentProfile.height] voxels. Without it those floors had no headroom at all, and the slab above has no
     * floor of theirs either, so ground just under a slab boundary was walkable in neither. Sea level is one:
     * dry lowland a metre under it was drawn as ground and refused as a wall. Asked for only when such a floor
     * comes up; null keeps the old answer, for a caller with no slab above to offer. [cellsAbove] is its pieces.
     */
    fun of(
      voxels: VoxelChunk,
      agent: AgentProfile = AgentProfile(),
      cells: StructureCells? = null,
      cellsAbove: StructureCells? = null,
      above: (() -> VoxelChunk)? = null
    ): WalkableTile {
      val size = voxels.size
      val columns = size * size
      val spanStart = IntArray(columns + 1)
      val found = ArrayList<Double>(columns)
      val flags = ArrayList<Byte>(columns)
      val upper = above?.let { lazy(LazyThreadSafetyMode.NONE, it) }

      // The top voxel is a floor only when there is a slab above it to stand in.
      val floors = if (upper == null) voxels.height - 1 else voxels.height

      for (localY in 0 until size) {
        for (localX in 0 until size) {
          val column = localY * size + localX
          spanStart[column] = found.size
          val offset = voxels.columnOffset(localX, localY)
          val built = BuiltColumn.of(cells, cellsAbove, localX, localY, voxels.height)

          var z = 0
          while (z < floors) {
            val builtFill = built?.fillAt(z) ?: Occupancy.EMPTY
            val onStructure = builtFill != Occupancy.EMPTY
            if (!onStructure) {
              val here = BlockType.ofOrNull(voxels.blocks[offset + z].toInt() and 0xFF)
              if (here == null || !here.solid) {
                z++
                continue
              }
            }

            if (hasClearance(voxels, offset, z + 1, agent, upper, built)) {
              val fill = if (onStructure) builtFill else Occupancy.unsigned(voxels.occupancy[offset + z])
              val surface = z + Occupancy.fractionOf(fill)
              val walls = built?.wallsAround(surface, agent.height) ?: Edges.NONE

              found.add(surface)
              flags.add((if (onStructure) walls or STRUCTURE_SPAN else walls).toByte())
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

      return WalkableTile(voxels.chunk, size, agent, spanStart, found.toDoubleArray(), flags.toByteArray())
    }

    /**
     * Whether an agent standing at [fromZ] has room, allowing for a shallow fluid to wade through. Headroom past
     * this slab's top is read from [above], at the same [offset]: chunks of one world share a shape, so a column
     * starts at the same index in both.
     */
    private fun hasClearance(
      voxels: VoxelChunk,
      offset: Int,
      fromZ: Int,
      agent: AgentProfile,
      above: Lazy<VoxelChunk>?,
      built: BuiltColumn?
    ): Boolean {
      val top = fromZ + agent.height
      if (top > voxels.height && above == null) return false

      // Summed as fractions rather than counted, because the topmost fluid voxel is partly full: an agent
      // whose wading limit is one voxel can stand in 1.0 of water but not 1.3, and counting voxels cannot
      // tell those apart.
      var wade = 0.0
      for (z in fromZ until top) {
        if (built != null && built.fillAt(z) != Occupancy.EMPTY) return false

        val slab = if (z < voxels.height) voxels else above!!.value
        val index = offset + if (z < voxels.height) z else z - voxels.height

        val block = BlockType.ofOrNull(slab.blocks[index].toInt() and 0xFF) ?: return false
        // Exhaustive with no `else`, so a new Passability value is a compile error here rather than a
        // material that silently becomes free headroom. That is what this replaced.
        when (block.passability) {
          Passability.OPEN -> Unit
          Passability.SWIMMABLE -> {
            wade += Occupancy.fractionOf(slab.occupancy[index].toInt() and 0xFF)
            if (wade > agent.maxWadeDepth) return false
          }
          Passability.BLOCKED -> return false
        }
      }

      return true
    }
  }

  /** The built cells of one column, in this slab and the one above, read with this slab's z. */
  private class BuiltColumn(
    private val cells: StructureCells?,
    private val cellsAbove: StructureCells?,
    private val localX: Int,
    private val localY: Int,
    private val slabHeight: Int
  ) {

    fun fillAt(z: Int): Int {
      if (z < slabHeight) return cells?.fillAt(localX, localY, z) ?: Occupancy.EMPTY

      return cellsAbove?.fillAt(localX, localY, z - slabHeight) ?: Occupancy.EMPTY
    }

    /** The walls a body standing on [surface] overlaps: every voxel from its feet to the top of its head. */
    fun wallsAround(surface: Double, bodyHeight: Int): Int {
      val feet = floor(surface).toInt()
      val head = ceil(surface + bodyHeight).toInt() - 1

      var walls = cells?.edgesBetween(localX, localY, feet, min(head, slabHeight - 1)) ?: Edges.NONE
      if (head >= slabHeight) {
        val above = cellsAbove?.edgesBetween(localX, localY, max(feet - slabHeight, 0), head - slabHeight)
        walls = walls or (above ?: Edges.NONE)
      }

      return walls
    }

    companion object {
      /** Null for a column no piece touches, which is nearly all of them. */
      fun of(
        cells: StructureCells?,
        cellsAbove: StructureCells?,
        localX: Int,
        localY: Int,
        slabHeight: Int
      ): BuiltColumn? {
        val here = cells != null && cells.hasColumn(localX, localY)
        val overhead = cellsAbove != null && cellsAbove.hasColumn(localX, localY)
        if (!here && !overhead) return null

        return BuiltColumn(cells, cellsAbove, localX, localY, slabHeight)
      }
    }
  }
}
