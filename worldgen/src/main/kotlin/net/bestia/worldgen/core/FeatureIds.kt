package net.bestia.worldgen.core

import net.bestia.worldgen.vector.FeatureId

/**
 * Assigns feature ids that are unique across the whole world and stable across runs.
 *
 * Ids matter more than they look. They are the tie-break for stamp order when two features have equal
 * priority, so if a river reach got id 7 on one node and id 12 on another, two chunks either side of a
 * confluence would blend the same pair of channels in different orders. Deriving the id from the
 * producing stage and an ordinal within that stage makes it a pure function of the pipeline, and
 * folding the stage in means two stages cannot collide however many features each emits.
 */
object FeatureIds {

  /** @param ordinal index within the producing stage; assign it from a stable iteration order. */
  fun of(stage: StageId, ordinal: Long): FeatureId = FeatureId(GenRng.hash(stage.hash, ordinal))

  /** A sequential allocator, for the common case of emitting a list in one pass. */
  fun allocator(stage: StageId): () -> FeatureId {
    var next = 0L
    return { of(stage, next++) }
  }

  /**
   * An allocator over one reserved block of a stage's ordinal space.
   *
   * For a stage that emits its features from independent units of work run in parallel - the town stage
   * lays out three hundred settlements, and which one finishes first is a property of the thread pool. A
   * single shared [allocator] would then hand out ordinals in completion order, and the ids, and with them
   * the stamp order of every equal-priority feature in the world, would depend on the scheduler. That is
   * precisely the failure the class note above exists to prevent.
   *
   * Giving unit `n` the ordinals `[n * stride, (n+1) * stride)` makes each unit's ids a pure function of
   * its own index and how many features it emits, so the result is identical whatever order the units run
   * in and however many workers there are. The ordinals are sparse rather than dense, which costs nothing:
   * [of] hashes them, so a dense range was never producing consecutive ids anyway.
   *
   * @param block index of the unit of work; must be stable across runs, not a completion counter
   */
  fun blockAllocator(stage: StageId, block: Int, stride: Long = BLOCK_STRIDE): () -> FeatureId {
    require(block >= 0) { "block must not be negative, was $block" }
    val base = block.toLong() * stride
    var next = 0L

    return {
      // A unit that overran its block would silently start issuing another unit's ids, and duplicate
      // feature ids collapse in the store's map rather than failing - so this is checked, not assumed.
      check(next < stride) { "$stage unit $block emitted more than $stride features" }
      of(stage, base + next++)
    }
  }

  /**
   * Ordinals reserved per unit of work.
   *
   * A million is far above what any one unit emits - the largest town in a reference world is a few
   * thousand buildings - and three hundred blocks of it is still nine orders of magnitude clear of where
   * a Long stops counting.
   */
  const val BLOCK_STRIDE = 1L shl 20
}
