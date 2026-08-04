package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk
import java.util.Locale

/**
 * What players have removed from one generated chunk.
 *
 * The generated world is a base layer and player changes are a sparse delta on top of it, which is what makes a
 * large world storable at all: an untouched chunk is stored as nothing, and a chunk read is
 * `base(generated, cached) ⊕ delta(persistent)`.
 *
 * ### Removals, not edits
 *
 * There is no building system and there never will be - the only terrain mutation the game has is removal, by
 * mining or by a spell that destroys landscape - so this holds **how much of each voxel is left**, and nothing
 * else. Three things follow, and they are the whole reason this type is shaped the way it is.
 *
 * **No block id.** It is derivable: a voxel that still has material has the material the generator gave it, and
 * one carved to nothing is [BlockType.AIR]. [mergedOnto] holds the base, so it reads the block from there. That
 * is not the same as omitting it to save a byte - the invariant that air has occupancy zero and everything else
 * does not is now preserved *locally, on both sides*, rather than carried and re-checked.
 *
 * **Occupancy only ever falls.** Enforced by the caller, which is the only party holding the base to compare
 * against - see `ChunkStore.carve`. A delta that could raise occupancy would be a placement system expressed by
 * accident, and every derived structure's monotonicity would go with it.
 *
 * **A voxel appears once.** Keyed on position rather than kept as a log, so a player who works the same voxel
 * over several swings costs one entry, not one per swing. That is also what makes [coverage] a size comparison.
 *
 * ### One sorted array, merged in batches
 *
 * Entries are `(voxelIndex shl 8) or remainingOccupancy` in a single `Int`: 18 bits of index and 8 of
 * occupancy, 26 in all. Because the index sits in the *high* bits, natural `Int` order **is** index order, so
 * the array is already sorted for the wire codec and for persistence, and on a collision the smaller packed
 * value is also the smaller occupancy - which is the one to keep.
 *
 * A sorted array rather than a hash map because a delta is walked far more often than it is written: [mergedOnto]
 * and the patch codec both want it in index order, and a `LinkedHashMap<Int, Int>` costs about twelve times the
 * memory per entry to hand back an order neither of them can use. The cost is that an insert is O(n), which is
 * why [carveAll] takes a whole batch: one brush application is one merge pass, not one insert per voxel.
 *
 * Not thread safe. One delta belongs to one chunk, and a chunk belongs to one owning node.
 */
class ChunkDelta(
  val chunk: ChunkPos,
  val size: Int,
  val height: Int
) {

  /** Sorted packed removals, valid up to [removalCount]. Replaced wholesale by each [carveAll]. */
  private var packed = IntArray(0)

  var removalCount: Int = 0
    private set

  val isEmpty get() = removalCount == 0

  val volume get() = size * size * height

  /** Fraction of the chunk's voxels that have been carved at all. */
  val coverage get() = removalCount.toDouble() / volume

  fun index(localX: Int, localY: Int, localZ: Int) = (localY * size + localX) * height + localZ

  /**
   * How much of the voxel at [voxelIndex] is left, or `-1` where this delta does not cover it.
   *
   * `-1` rather than null so the hot path - `ChunkStore.carve` asking what a voxel currently holds, once per
   * voxel of a brush - does not box an `Int` per question.
   */
  fun remainingAt(voxelIndex: Int): Int {
    val at = search(voxelIndex)
    return if (at < 0) -1 else packed[at] and 0xFF
  }

  fun remainingAt(localX: Int, localY: Int, localZ: Int) = remainingAt(index(localX, localY, localZ))

  /**
   * Records a batch of removals, keeping whichever occupancy is lower where one is already held.
   *
   * @param removals packed `(voxelIndex shl 8) or remainingOccupancy`, **sorted ascending**, each index at most
   *   once. `CarveBrush` walks a chunk in index order, so a caller building one brush's removals gets that for
   *   free; [pack] is the only thing that should ever build an entry.
   * @return how many voxels this actually changed, which is not `removals.size` - a voxel already carved at or
   *   below the offered occupancy is not a change, and the caller needs to know that to decide whether the
   *   chunk's revision should move at all.
   */
  fun carveAll(removals: IntArray): Int {
    if (removals.isEmpty()) return 0

    var previousIndex = -1
    for (entry in removals) {
      val entryIndex = entry ushr 8
      require(entryIndex > previousIndex) {
        "Removals must be sorted with each index at most once; $entryIndex followed $previousIndex"
      }
      require(entryIndex < volume) { "Voxel index $entryIndex is outside a ${size}x${size}x$height chunk" }
      previousIndex = entryIndex
    }

    val merged = IntArray(removalCount + removals.size)
    var out = 0
    var mine = 0
    var theirs = 0
    var changed = 0

    while (mine < removalCount && theirs < removals.size) {
      val mineIndex = packed[mine] ushr 8
      val theirsIndex = removals[theirs] ushr 8

      when {
        mineIndex < theirsIndex -> merged[out++] = packed[mine++]
        theirsIndex < mineIndex -> {
          merged[out++] = removals[theirs++]
          changed++
        }
        // Same voxel: the lower occupancy wins, and because the index is in the high bits that is simply the
        // lower packed value. Occupancy never rises, so an offer above what is held is silently the no-op it
        // should be rather than an error.
        else -> {
          val held = packed[mine]
          val offered = removals[theirs]

          if (offered < held) changed++
          merged[out++] = if (offered < held) offered else held
          mine++
          theirs++
        }
      }
    }

    while (mine < removalCount) merged[out++] = packed[mine++]
    while (theirs < removals.size) {
      merged[out++] = removals[theirs++]
      changed++
    }

    packed = merged
    removalCount = out

    return changed
  }

  /** The removals in index order, exactly as stored. What the patch codec and persistence both want. */
  fun packedRemovals(): IntArray = packed.copyOf(removalCount)

  /**
   * Applies this delta onto a copy of [base] and returns the merged chunk.
   *
   * The server has to hold merged voxel state - it is not a design choice. Line of sight, projectile collision,
   * movement validation and NPC pathing all need the server's own view of geometry, and a server that cannot
   * answer "is there a wall between these two players" will have players dig until they find out what happens.
   *
   * The merge itself is deliberately cheap and not worth optimising: overlaying even a hundred thousand removals
   * onto a decoded base is memcpy-scale. The expensive part is *regenerating* the base, which is what
   * [net.bestia.worldgen.store.ChunkCache] exists for.
   */
  fun mergedOnto(base: VoxelChunk): VoxelChunk {
    require(base.chunk == chunk) { "delta for $chunk applied to ${base.chunk}" }
    require(base.size == size && base.height == height) {
      "delta is ${size}x${size}x$height, base is ${base.size}x${base.size}x${base.height}"
    }

    val merged = base.copy()
    for (i in 0 until removalCount) {
      val entry = packed[i]
      val position = entry ushr 8
      val remaining = entry and 0xFF

      if (remaining == Occupancy.EMPTY) {
        merged.blocks[position] = AIR_ID
        merged.occupancy[position] = Occupancy.EMPTY_BYTE
      } else {
        // The block stays whatever the generator put there; only how much of it is left has changed. A voxel
        // that was already air cannot reach this arm, because a removal against air is dropped at record time.
        merged.occupancy[position] = remaining.toByte()
      }
    }
    return merged
  }

  /** The columns this delta touches, so a derived structure can rebuild only what changed. */
  fun touchedColumns(): Set<Int> {
    val columns = HashSet<Int>()
    for (i in 0 until removalCount) {
      columns.add((packed[i] ushr 8) / height)
    }
    return columns
  }

  /**
   * Whether this delta has grown past the point where storing it as a delta is still a saving.
   *
   * **The size test is the one that fires, and it is not the coverage test.** A delta stops being cheaper than
   * the chunk it modifies at under three percent of the chunk's voxels, so [BAKE_COVERAGE] at thirty percent is
   * an order of magnitude too late to be the trigger - it is a backstop for the case where the reference size is
   * unusually large. `StorageBudgetTest` asserts that ordering, because getting it wrong means quietly storing
   * ten times the chunk as a delta before anything decides to bake it.
   *
   * Baking is worth keeping even though a removal-only delta is *bounded* - a voxel can only be carved down, so
   * the entry count cannot exceed the chunk's volume. The bound is simply large: a fully worked-out chunk is a
   * megabyte of packed removals, where the same chunk baked is nearly all air and run-length encodes to a few
   * dozen bytes. Heavily mined ground therefore becomes *cheaper* rather than merely smaller, and reads skip
   * generation entirely.
   *
   * @param referenceBytes size of this chunk when RLE encoded. **A cached figure, not a fresh encode** - see
   *   `ChunkStore.compact`. Computing it per call is what used to put a full chunk copy and a full encode on the
   *   path of every removed voxel.
   */
  fun shouldBake(referenceBytes: Int): Boolean =
    coverage >= BAKE_COVERAGE || estimatedBytes() >= referenceBytes

  /** Rough stored size of this delta: a varint index and an occupancy per removal. */
  fun estimatedBytes(): Int = removalCount * BYTES_PER_REMOVAL

  /** Index of [voxelIndex] in [packed], or a negative value if it is not held. */
  private fun search(voxelIndex: Int): Int {
    var low = 0
    var high = removalCount - 1

    while (low <= high) {
      val mid = (low + high) ushr 1
      val midIndex = packed[mid] ushr 8

      when {
        midIndex < voxelIndex -> low = mid + 1
        midIndex > voxelIndex -> high = mid - 1
        else -> return mid
      }
    }

    return -1
  }

  override fun toString() =
    "ChunkDelta[$chunk, $removalCount removals, ${"%.1f".format(Locale.ROOT, coverage * 100)}% of the chunk]"

  companion object {

    private val AIR_ID = BlockType.AIR.id.toByte()

    /**
     * Fraction of a chunk's voxels beyond which it is baked regardless of the size test.
     *
     * A backstop rather than the trigger. See [shouldBake].
     */
    const val BAKE_COVERAGE = 0.30

    /**
     * Stored bytes per removal: a varint index plus the remaining occupancy.
     *
     * Three, where an edit that also carried a block id took five. A voxel index tops out at 262 143, which needs
     * three varint bytes on its own - but removals are stored and sent **delta coded** against the previous
     * index, and because the vertical axis is contiguous the removals in one column are adjacent, so nearly every
     * gap is one byte. Three is therefore a generous upper bound, which is the safe direction: it errs towards
     * baking, and baking is merely eager rather than wrong.
     */
    const val BYTES_PER_REMOVAL = 3

    /** The only thing that should build a stored entry. */
    fun pack(voxelIndex: Int, remainingOccupancy: Int): Int {
      require(voxelIndex >= 0) { "Voxel index $voxelIndex is negative" }
      require(remainingOccupancy in 0..255) { "Occupancy must fit a byte, was $remainingOccupancy" }
      return (voxelIndex shl 8) or remainingOccupancy
    }

    fun indexOf(packed: Int) = packed ushr 8

    fun remainingOf(packed: Int) = packed and 0xFF

    /**
     * Bakes a delta into a new base: the merged chunk, RLE encoded, ready to store under the chunk coordinate.
     *
     * Also the migration path for a pipeline change. Once a world ships its pipeline version is frozen, because
     * any change shifts the base under the player's removals - "this voxel is now a third full" starts meaning a
     * third of different rock. To upgrade: bake every chunk that has a delta, then change the pipeline.
     * Unmodified chunks regenerate against the new version harmlessly.
     */
    fun bake(base: VoxelChunk, delta: ChunkDelta): ByteArray =
      RleCodec.encode(delta.mergedOnto(base))
  }
}
