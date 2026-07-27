package net.bestia.worldgen.store

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * Hash of a generated base chunk.
 *
 * Sent alongside a delta when the client generates its own base, so the client can verify that what it
 * produced matches what the server produced. The point is not to catch a bug in the pipeline; it is to
 * turn the failure mode that *would* occur - a silent desync where the player sees ground and the server
 * sees air, walks into a wall that is not there, and files an incomprehensible bug report - into a
 * bandwidth blip, because on mismatch the client discards its base and asks for the full merged chunk.
 */
object BaseHash {

  fun of(voxels: VoxelChunk): Long {
    // FNV-1a over the block bytes, folded through the mixer so that similar chunks are not similar hashes.
    var h = -0x340d631b7bdddcdbL
    for (b in voxels.blocks) {
      h = h xor (b.toLong() and 0xFF)
      h *= 0x100000001b3L
    }
    return GenRng.mix64(h xor voxels.chunk.key())
  }
}

/**
 * The full picture of one chunk's storage: the generated base, the player's edits on top, and the baked
 * replacement once the edits have outgrown being a delta.
 *
 * A read is `base(generated, cached) ⊕ delta(persistent)`, which means an untouched chunk is stored as
 * nothing at all. On a large world most chunks a player crosses are untouched, so this is where the storage
 * saving of the whole design lives.
 *
 * Not thread safe; a chunk has one owning node.
 */
class ChunkStore(
  private val config: WorldConfig,
  private val cache: ChunkCache,
  /** Where baked chunks live. Authoritative once a chunk is baked - generation is skipped entirely. */
  private val baked: ChunkBlobStore = MemoryBlobStore(),
  /** Called when a chunk's contents change, so derived structures can be marked stale. */
  private val onChanged: (ChunkPos) -> Unit = {}
) {

  private val deltas = LinkedHashMap<ChunkPos, ChunkDelta>()
  private val bakedChunks = LinkedHashSet<ChunkPos>()

  val deltaCount get() = deltas.size
  val bakedCount get() = bakedChunks.size

  fun isBaked(chunk: ChunkPos) = chunk in bakedChunks

  fun deltaOf(chunk: ChunkPos): ChunkDelta? = deltas[chunk]

  /**
   * The chunk as it actually is: baked if it has been, otherwise the base with any delta applied.
   *
   * This is what the server is authoritative over. Line of sight, projectile collision, movement
   * validation and NPC pathing all need the server's own view of geometry - a server without merged voxels
   * cannot answer "is there a wall between these two players", and players will build walls specifically to
   * find out what happens when it cannot.
   */
  fun merged(chunk: ChunkPos): VoxelChunk {
    if (chunk in bakedChunks) {
      val blob = baked.get(bakedKeyOf(chunk))
      if (blob != null) return RleCodec.decode(chunk, blob)
      // A baked chunk whose blob is gone is a storage failure, not a normal state. Falling back to
      // regeneration loses the player's work silently, so say so instead.
      throw IllegalStateException("$chunk is marked baked but its blob is missing")
    }

    val base = cache.base(chunk)
    val delta = deltas[chunk] ?: return base
    return delta.mergedOnto(base)
  }

  /** The generated base only, with no edits - what a client would produce for itself. */
  fun base(chunk: ChunkPos): VoxelChunk = cache.base(chunk)

  /** Hash of the generated base, for a client to check its own generation against. */
  fun baseHash(chunk: ChunkPos): Long = BaseHash.of(cache.base(chunk))

  /**
   * Records one edit, and bakes the chunk if the delta has outgrown its usefulness.
   *
   * @return true when the edit caused the chunk to be baked
   */
  fun edit(chunk: ChunkPos, localX: Int, localY: Int, localZ: Int, block: BlockType): Boolean {
    if (chunk in bakedChunks) {
      // Already baked: edit the stored blob directly. There is no delta to grow.
      val current = merged(chunk)
      current[localX, localY, localZ] = block
      baked.put(bakedKeyOf(chunk), RleCodec.encode(current))
      onChanged(chunk)
      return false
    }

    val delta = deltas.getOrPut(chunk) { ChunkDelta(chunk, config.chunkSize, config.chunkHeight) }
    delta.set(localX, localY, localZ, block)
    onChanged(chunk)

    return compact(chunk)
  }

  /**
   * Bakes a chunk if its delta has grown past the point where keeping it as a delta saves anything.
   *
   * Compaction is mandatory rather than an optimisation. Deltas are unbounded - a player who terraforms a
   * hillside over six months accumulates a delta larger than the chunk itself - and past the threshold it
   * is cheaper in both space and time to store the merged result and drop the delta. Reads then skip
   * generation entirely, so heavily built areas become *cheaper* rather than merely smaller.
   *
   * @return true when the chunk was baked
   */
  fun compact(chunk: ChunkPos): Boolean {
    val delta = deltas[chunk] ?: return false

    val base = cache.base(chunk)
    val merged = delta.mergedOnto(base)
    val encoded = RleCodec.encode(merged)

    if (!delta.shouldBake(encoded.size)) return false

    baked.put(bakedKeyOf(chunk), encoded)
    bakedChunks.add(chunk)
    deltas.remove(chunk)
    // The generated base for this coordinate will never be read again.
    cache.evict(chunk)

    return true
  }

  /**
   * Bakes every chunk that currently has a delta.
   *
   * The migration path for a pipeline change, and the only safe one. Once a world ships, its pipeline
   * version is frozen: any change shifts the base out from under the player's deltas, so an edit recorded
   * as "this voxel is now air" starts meaning something different. Baking first pins the current result;
   * unmodified chunks then regenerate against the new version harmlessly.
   */
  fun bakeAll(): Int {
    val pending = deltas.keys.toList()
    var count = 0
    for (chunk in pending) {
      val base = cache.base(chunk)
      val delta = deltas.getValue(chunk)
      baked.put(bakedKeyOf(chunk), ChunkDelta.bake(base, delta))
      bakedChunks.add(chunk)
      deltas.remove(chunk)
      cache.evict(chunk)
      count++
    }
    return count
  }

  /**
   * Baked blobs are keyed on the coordinate and the *seed* but deliberately **not** on the pipeline
   * version: a baked chunk no longer depends on the pipeline that produced it, which is exactly the
   * property that makes baking a migration path.
   */
  private fun bakedKeyOf(chunk: ChunkPos) = GenRng.hash(config.seed, BAKED_SALT, chunk.key())

  override fun toString() = "ChunkStore[${deltas.size} deltas, ${bakedChunks.size} baked]"

  private companion object {
    const val BAKED_SALT = 0x1BA6EDL
  }
}
