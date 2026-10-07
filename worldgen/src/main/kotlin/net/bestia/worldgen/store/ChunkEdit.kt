package net.bestia.worldgen.store

/** What a [ChunkStore] holds for one edited chunk, in the form it holds it. */
sealed class ChunkEdit {

  /** [net.bestia.worldgen.derived.VoxelEdit]s over the generated base, sorted as `ChunkDelta` keeps them. */
  class Delta(val edits: LongArray) : ChunkEdit()

  /** The whole chunk as `RleCodec` encodes it, once its edits outgrew a delta. */
  class Baked(val rle: ByteArray) : ChunkEdit()
}
