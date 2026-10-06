package net.bestia.worldgen.store

/** What a [ChunkStore] holds for one edited chunk, in the form it holds it. */
sealed class ChunkEdit {

  /** Removals over the generated base, packed and sorted as `ChunkDelta` keeps them. */
  class Delta(val removals: IntArray) : ChunkEdit()

  /** The whole chunk as `RleCodec` encodes it, once its edits outgrew a delta. */
  class Baked(val rle: ByteArray) : ChunkEdit()
}
