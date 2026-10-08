package net.bestia.zone.water.sim

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import java.util.BitSet
import kotlin.math.abs

/**
 * One chunk's water as the simulation holds it: how full each cell is, which cells water can never enter, and which
 * hold generated water that never changes.
 *
 * A cell's fill is its voxel occupancy, so a cell that changed is exactly the [VoxelEdit] it is committed as. Cells
 * use the chunk's own voxel index, so the vertical axis is contiguous.
 */
class WaterChunk(val pos: ChunkPos, val size: Int, val height: Int) {

  val volume = size * size * height

  private val fill = ByteArray(volume)
  private val walls = BitSet(volume)
  private val sources = BitSet(volume)

  /** Cells to update in the running pass. Each is cleared as the stepper reaches it. */
  internal var active = BitSet(volume)

  /** Cells to update in the next pass: every changed cell and its six neighbours. */
  internal var nextActive = BitSet(volume)

  private val committed = ByteArray(volume)
  private val dirty = BitSet(volume)

  /** Links to the six neighbouring chunks, indexed by [Face.ordinal]; null where a neighbour is not loaded. */
  internal val neighbours = arrayOfNulls<WaterChunk>(Face.entries.size)

  /** The largest change to one cell in the running pass. */
  internal var largestChange = 0

  /** Passes in a row whose largest change was at most [SETTLED_CHANGE]. */
  internal var quietPasses = 0

  /** Whether a cell moved far enough since the last [takeEdits] to be worth telling the world. */
  var hasEditsWorthCommitting = false
    private set

  val isAsleep: Boolean
    get() = active.isEmpty && nextActive.isEmpty

  fun fillAt(index: Int): Int {
    return fill[index].toInt() and 0xFF
  }

  fun isWall(index: Int): Boolean {
    return walls[index]
  }

  fun isSource(index: Int): Boolean {
    return sources[index]
  }

  /** The total fill of every cell that is not generated water. Conserved by every move between such cells. */
  fun movableWater(): Long {
    var total = 0L
    for (index in 0 until volume) {
      if (!isSource(index)) total += fillAt(index)
    }
    return total
  }

  /** Marks [index] and its six neighbours for the next pass. */
  fun wake(index: Int) {
    nextActive.set(index)
    for (face in Face.entries) {
      neighbourChunk(index, face)?.nextActive?.set(neighbourIndex(index, face))
    }
  }

  internal fun setFill(index: Int, value: Int) {
    val before = fillAt(index)
    if (before == value) return

    fill[index] = value.toByte()
    dirty.set(index)

    val change = abs(value - before)
    if (change > largestChange) largestChange = change

    val committedFill = committed[index].toInt() and 0xFF
    if ((committedFill == 0) != (value == 0) || abs(value - committedFill) >= COMMIT_STEP) {
      hasEditsWorthCommitting = true
    }
  }

  /** The cells that changed since the last call, as sorted [VoxelEdit]s, which then count as committed. */
  fun takeEdits(): LongArray {
    val edits = ArrayList<Long>(dirty.cardinality())

    var index = dirty.nextSetBit(0)
    while (index >= 0) {
      val value = fillAt(index)
      if (value != committed[index].toInt() and 0xFF) {
        val block = if (value == Occupancy.EMPTY) BlockType.AIR else BlockType.WATER
        edits.add(VoxelEdit.pack(index, block, value))
        committed[index] = value.toByte()
      }
      index = dirty.nextSetBit(index + 1)
    }

    dirty.clear()
    hasEditsWorthCommitting = false
    return edits.toLongArray()
  }

  /** Whether stepping from [index] across [face] leaves this chunk. */
  fun crossesBorder(index: Int, face: Face): Boolean {
    return when (face) {
      Face.DOWN -> index % height == 0
      Face.UP -> index % height == height - 1
      Face.WEST -> (index / height) % size == 0
      Face.EAST -> (index / height) % size == size - 1
      Face.SOUTH -> index / (height * size) == 0
      Face.NORTH -> index / (height * size) == size - 1
    }
  }

  /** The index of [index]'s neighbour across [face], in whichever chunk [neighbourChunk] names. */
  fun neighbourIndex(index: Int, face: Face): Int {
    val crossing = crossesBorder(index, face)
    val row = size * height

    return when (face) {
      Face.DOWN -> if (crossing) index + height - 1 else index - 1
      Face.UP -> if (crossing) index - (height - 1) else index + 1
      Face.WEST -> if (crossing) index + (size - 1) * height else index - height
      Face.EAST -> if (crossing) index - (size - 1) * height else index + height
      Face.SOUTH -> if (crossing) index + (size - 1) * row else index - row
      Face.NORTH -> if (crossing) index - (size - 1) * row else index + row
    }
  }

  /** The chunk holding [index]'s neighbour across [face]: this one, a linked one, or null if it is not loaded. */
  fun neighbourChunk(index: Int, face: Face): WaterChunk? {
    return if (crossesBorder(index, face)) neighbours[face.ordinal] else this
  }

  /** Swaps in the next pass's cells, and puts the chunk to sleep after [QUIET_PASSES_TO_SLEEP] quiet passes. */
  internal fun endPass() {
    if (isAsleep) {
      quietPasses = 0
      largestChange = 0
      return
    }

    val finished = active
    active = nextActive
    nextActive = finished
    nextActive.clear()

    quietPasses = if (largestChange <= SETTLED_CHANGE) quietPasses + 1 else 0
    largestChange = 0

    // A safety net: rounding against a source can trade a unit back and forth for ever without settling.
    if (quietPasses >= QUIET_PASSES_TO_SLEEP) {
      active.clear()
      quietPasses = 0
    }
  }

  internal fun loadWall(index: Int) {
    walls.set(index)
  }

  internal fun loadSource(index: Int, level: Int) {
    sources.set(index)
    fill[index] = level.toByte()
    committed[index] = level.toByte()
  }

  internal fun loadFill(index: Int, level: Int) {
    fill[index] = level.toByte()
    committed[index] = level.toByte()
  }

  override fun toString(): String {
    return "WaterChunk[$pos, ${if (isAsleep) "asleep" else "awake"}]"
  }

  companion object {

    /** A pass whose largest change is this or less counts as quiet. */
    const val SETTLED_CHANGE = 2

    const val QUIET_PASSES_TO_SLEEP = 50

    /** A cell is worth committing once it turned to or from air, or moved by this much since the last commit. */
    const val COMMIT_STEP = 32

    /**
     * The water of a loaded chunk. A voxel whose generated block is water is a source; a voxel that is air, or
     * water the simulation put there, can hold water; everything else, ice and lava included, is a wall.
     */
    fun of(merged: VoxelChunk, base: VoxelChunk): WaterChunk {
      require(merged.chunk == base.chunk) { "merged ${merged.chunk} does not match base ${base.chunk}" }

      val chunk = WaterChunk(merged.chunk, merged.size, merged.height)
      val air = BlockType.AIR.id.toByte()
      val water = BlockType.WATER.id.toByte()

      for (index in 0 until chunk.volume) {
        val block = merged.blocks[index]
        val level = Occupancy.unsigned(merged.occupancy[index])

        when {
          block == water && base.blocks[index] == water -> chunk.loadSource(index, level)
          block == water -> chunk.loadFill(index, level)
          block != air -> chunk.loadWall(index)
        }
      }

      return chunk
    }
  }
}
