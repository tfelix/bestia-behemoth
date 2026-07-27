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
}
