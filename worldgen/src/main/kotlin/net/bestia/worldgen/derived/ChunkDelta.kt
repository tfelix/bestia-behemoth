package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk
import java.util.Locale

/**
 * What has changed in one generated chunk.
 *
 * The generated world is a base layer and changes are a sparse delta on top of it, which is what makes a large
 * world storable at all: an untouched chunk is stored as nothing, and a chunk read is
 * `base(generated, cached) ⊕ delta(persistent)`.
 *
 * Each entry is a [VoxelEdit], the voxel's whole new state. A voxel appears once, so working it over many swings
 * costs one entry, and an edit that puts a voxel back to its base removes the entry instead of storing it.
 *
 * ### One sorted array, merged in batches
 *
 * A sorted array rather than a hash map because a delta is walked far more often than it is written: [mergedOnto]
 * and the codecs both want it in index order, and a `LinkedHashMap` costs many times the memory per entry to hand
 * back an order neither of them can use. The cost is that an insert is O(n), which is why [editAll] takes a whole
 * batch: one brush application is one merge pass, not one insert per voxel.
 *
 * Not thread safe. One delta belongs to one chunk, and a chunk belongs to one owning node.
 */
class ChunkDelta(
  val chunk: ChunkPos,
  val size: Int,
  val height: Int
) {

  /** Sorted [VoxelEdit]s, valid up to [editCount]. Replaced wholesale by each [editAll]. */
  private var edits = LongArray(0)

  var editCount: Int = 0
    private set

  val isEmpty get() = editCount == 0

  val volume get() = size * size * height

  /** Fraction of the chunk's voxels that differ from the base. */
  val coverage get() = editCount.toDouble() / volume

  fun index(localX: Int, localY: Int, localZ: Int) = (localY * size + localX) * height + localZ

  /**
   * The edit held for [voxelIndex], or [NONE] where this delta does not cover it.
   *
   * [NONE] rather than null so the hot path - a carve asking what each voxel of a brush holds - does not box a
   * `Long` per question.
   */
  fun editAt(voxelIndex: Int): Long {
    val at = search(voxelIndex)
    return if (at < 0) NONE else edits[at]
  }

  /**
   * Records a batch of edits over [base]: the last write wins, and an edit equal to the base drops the entry.
   *
   * @param batch [VoxelEdit]s sorted ascending, each index at most once
   * @return how many voxels this actually changed, which is not `batch.size`: an edit that repeats what the voxel
   *   already holds is not a change, and the caller needs to know that to decide whether the revision moves
   */
  fun editAll(batch: LongArray, base: VoxelChunk): Int {
    requireMatches(base)
    requireSorted(batch, volume)
    if (batch.isEmpty()) return 0

    val merged = LongArray(editCount + batch.size)
    var out = 0
    var mine = 0
    var theirs = 0
    var changed = 0

    while (mine < editCount || theirs < batch.size) {
      val mineIndex = if (mine < editCount) VoxelEdit.indexOf(edits[mine]) else Int.MAX_VALUE
      val theirsIndex = if (theirs < batch.size) VoxelEdit.indexOf(batch[theirs]) else Int.MAX_VALUE

      if (mineIndex < theirsIndex) {
        merged[out++] = edits[mine++]
        continue
      }

      val offered = batch[theirs++]
      val generated = VoxelEdit.of(base, theirsIndex)
      val held = if (mineIndex == theirsIndex) edits[mine++] else generated

      if (offered != held) changed++
      if (offered != generated) merged[out++] = offered
    }

    edits = merged
    editCount = out

    return changed
  }

  /** The edits in index order, exactly as stored. What the codecs and persistence want. */
  fun edits(): LongArray {
    return edits.copyOf(editCount)
  }

  /**
   * Applies this delta onto a copy of [base] and returns the merged chunk.
   *
   * The server has to hold merged voxel state - it is not a design choice. Line of sight, projectile collision,
   * movement validation and NPC pathing all need the server's own view of geometry.
   *
   * The merge itself is cheap: overlaying even a hundred thousand edits onto a decoded base is memcpy-scale. The
   * expensive part is *regenerating* the base, which is what [net.bestia.worldgen.store.ChunkCache] exists for.
   */
  fun mergedOnto(base: VoxelChunk): VoxelChunk {
    requireMatches(base)

    val merged = base.copy()
    for (i in 0 until editCount) {
      val edit = edits[i]
      val position = VoxelEdit.indexOf(edit)

      merged.blocks[position] = VoxelEdit.blockIdOf(edit).toByte()
      merged.occupancy[position] = VoxelEdit.occupancyOf(edit).toByte()
    }
    return merged
  }

  /** The columns this delta touches, so a derived structure can rebuild only what changed. */
  fun touchedColumns(): Set<Int> {
    val columns = HashSet<Int>()
    for (i in 0 until editCount) {
      columns.add(VoxelEdit.indexOf(edits[i]) / height)
    }
    return columns
  }

  /**
   * Whether this delta has grown past the point where storing it as a delta is still a saving.
   *
   * **The size test is the one that fires, and it is not the coverage test.** A delta stops being cheaper than
   * the chunk it modifies at a few percent of the chunk's voxels, so [BAKE_COVERAGE] at thirty percent is
   * a backstop for the case where the reference size is unusually large. `StorageBudgetTest` asserts that
   * ordering, because getting it wrong means quietly storing ten times the chunk as a delta before anything
   * decides to bake it.
   *
   * @param referenceBytes size of this chunk when RLE encoded. **A cached figure, not a fresh encode** - see
   *   `ChunkStore.compact`.
   */
  fun shouldBake(referenceBytes: Int): Boolean {
    return coverage >= BAKE_COVERAGE || estimatedBytes() >= referenceBytes
  }

  /** Rough stored size of this delta: a varint index gap, a block and an occupancy per edit. */
  fun estimatedBytes(): Int {
    return editCount * BYTES_PER_EDIT
  }

  /** Index of [voxelIndex] in [edits], or a negative value if it is not held. */
  private fun search(voxelIndex: Int): Int {
    var low = 0
    var high = editCount - 1

    while (low <= high) {
      val mid = (low + high) ushr 1
      val midIndex = VoxelEdit.indexOf(edits[mid])

      when {
        midIndex < voxelIndex -> low = mid + 1
        midIndex > voxelIndex -> high = mid - 1
        else -> return mid
      }
    }

    return -1
  }

  private fun requireMatches(base: VoxelChunk) {
    require(base.chunk == chunk) { "delta for $chunk applied to ${base.chunk}" }
    require(base.size == size && base.height == height) {
      "delta is ${size}x${size}x$height, base is ${base.size}x${base.size}x${base.height}"
    }
  }

  override fun toString(): String {
    return "ChunkDelta[$chunk, $editCount edits, ${"%.1f".format(Locale.ROOT, coverage * 100)}% of the chunk]"
  }

  companion object {

    /** What [editAt] answers for a voxel this delta does not cover. No real edit packs to it. */
    const val NONE = -1L

    /**
     * Fraction of a chunk's voxels beyond which it is baked regardless of the size test.
     *
     * A backstop rather than the trigger. See [shouldBake].
     */
    const val BAKE_COVERAGE = 0.30

    /**
     * Stored bytes per edit: a varint index gap, the block and the occupancy.
     *
     * Edits are stored **delta coded** against the previous index, and because the vertical axis is contiguous
     * the edits in one column are adjacent, so nearly every gap is one byte.
     */
    const val BYTES_PER_EDIT = 3

    /** A delta holding [edits] exactly as an earlier [edits] call handed them out, as persistence restores it. */
    fun of(chunk: ChunkPos, size: Int, height: Int, edits: LongArray): ChunkDelta {
      val delta = ChunkDelta(chunk, size, height)
      requireSorted(edits, delta.volume)
      delta.edits = edits.copyOf()
      delta.editCount = edits.size
      return delta
    }

    /** The order every batch of edits must come in: ascending, each index at most once, inside the chunk. */
    fun requireSorted(edits: LongArray, volume: Int) {
      var previousIndex = -1
      for (edit in edits) {
        val index = VoxelEdit.indexOf(edit)
        require(index > previousIndex) { "Edits must be sorted with each index at most once; $index followed $previousIndex" }
        require(index < volume) { "Voxel index $index is outside a chunk of $volume voxels" }
        previousIndex = index
      }
    }

    /** A removal as `ChunkStore.carve` takes it: `(voxelIndex shl 8) or remainingOccupancy`. */
    fun pack(voxelIndex: Int, remainingOccupancy: Int): Int {
      require(voxelIndex >= 0) { "Voxel index $voxelIndex is negative" }
      require(remainingOccupancy in 0..255) { "Occupancy must fit a byte, was $remainingOccupancy" }
      return (voxelIndex shl 8) or remainingOccupancy
    }

    fun indexOf(removal: Int): Int {
      return removal ushr 8
    }

    fun remainingOf(removal: Int): Int {
      return removal and 0xFF
    }

    /**
     * Bakes a delta into a new base: the merged chunk, RLE encoded, ready to store under the chunk coordinate.
     *
     * Also the migration path for a pipeline change. Once a world ships its pipeline version is frozen, because
     * any change shifts the base under the stored edits. To upgrade: bake every chunk that has a delta, then
     * change the pipeline. Unmodified chunks regenerate against the new version harmlessly.
     */
    fun bake(base: VoxelChunk, delta: ChunkDelta): ByteArray {
      return RleCodec.encode(delta.mergedOnto(base))
    }
  }
}
