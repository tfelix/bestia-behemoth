package net.bestia.worldgen.store

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
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
    // FNV-1a over material and then occupancy, folded through the mixer so that similar chunks are not
    // similar hashes. Occupancy has to be in here: it is the sub-voxel half of the geometry, so a client
    // whose floats put a surface one 255th of a voxel off would otherwise pass the check and then disagree
    // with the server about exactly the thing this hash exists to catch.
    var h = -0x340d631b7bdddcdbL
    for (b in voxels.blocks) {
      h = h xor (b.toLong() and 0xFF)
      h *= 0x100000001b3L
    }
    for (b in voxels.occupancy) {
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
   * Carves a batch of voxels, and bakes the chunk if its delta has outgrown its usefulness.
   *
   * **A batch, not a voxel**, and that is a requirement rather than an optimisation. One brush application is
   * one call: `ChunkDelta` merges a sorted batch in a single pass, the change is announced once, and the
   * revision moves once. Calling this per voxel of a brush would produce seventy revisions and seventy patches
   * for one swing, and would put the O(n) delta merge inside a loop over n.
   *
   * ### Occupancy only ever falls, and this is where that is enforced
   *
   * This is the only party holding the base, so it is the only one that can compare an offered occupancy
   * against what a voxel currently has. A removal that would *raise* occupancy is refused outright rather than
   * clamped, because it is a placement system arriving by accident, not a rounding disagreement. A removal that
   * changes nothing - a voxel already at or below the offered fill, or one the generator left as air - is
   * dropped silently, which is what keeps `ChunkDelta.removalCount` an honest count of changed voxels and stops
   * a player grinding at bare rock from bumping the revision.
   *
   * @param removals packed `(voxelIndex shl 8) or remainingOccupancy`, sorted ascending
   * @return how many voxels actually changed; zero means nothing was announced and the revision did not move
   */
  fun carve(chunk: ChunkPos, removals: IntArray): CarveOutcome {
    if (removals.isEmpty()) return CarveOutcome(0, false)

    if (chunk in bakedChunks) {
      // Already baked: carve the stored blob directly. There is no delta to grow.
      val current = merged(chunk)
      var changed = 0

      for (entry in removals) {
        if (applyTo(current, ChunkDelta.indexOf(entry), ChunkDelta.remainingOf(entry))) changed++
      }

      if (changed == 0) return CarveOutcome(0, false)

      baked.put(bakedKeyOf(chunk), RleCodec.encode(current))
      onChanged(chunk)
      return CarveOutcome(changed, false)
    }

    val base = cache.base(chunk)
    val delta = deltas[chunk]

    // Filter against what the voxel currently holds before touching the delta, so an offer that changes
    // nothing never becomes an entry and never widens the batch the wire has to carry.
    val effective = IntArray(removals.size)
    var kept = 0

    for (entry in removals) {
      val position = ChunkDelta.indexOf(entry)
      val offered = ChunkDelta.remainingOf(entry)
      val held = delta?.remainingAt(position)?.takeIf { it >= 0 }
        ?: (base.occupancy[position].toInt() and 0xFF)

      require(offered <= held) {
        "$chunk voxel $position is at occupancy $held; a carve to $offered would add material"
      }

      if (offered < held) effective[kept++] = entry
    }

    if (kept == 0) return CarveOutcome(0, false)

    val target = deltas.getOrPut(chunk) { ChunkDelta(chunk, config.chunkSize, config.chunkHeight) }
    val changed = target.carveAll(if (kept == removals.size) effective else effective.copyOf(kept))
    onChanged(chunk)

    return CarveOutcome(changed, compact(chunk))
  }

  /**
   * Writes one removal into a chunk in place, returning whether it changed anything.
   *
   * Used for the baked path, where there is no delta to compare against and the blob is the truth.
   */
  private fun applyTo(chunk: VoxelChunk, position: Int, remaining: Int): Boolean {
    val held = chunk.occupancy[position].toInt() and 0xFF

    require(remaining <= held) {
      "${chunk.chunk} voxel $position is at occupancy $held; a carve to $remaining would add material"
    }

    if (remaining == held) return false

    if (remaining == Occupancy.EMPTY) {
      chunk.blocks[position] = BlockType.AIR.id.toByte()
      chunk.occupancy[position] = Occupancy.EMPTY_BYTE
    } else {
      chunk.occupancy[position] = remaining.toByte()
    }

    return true
  }

  /** What one [carve] did, so a caller knows whether to announce it and whether the chunk was rewritten. */
  data class CarveOutcome(
    /** Voxels whose occupancy actually fell. Zero means nothing happened. */
    val changed: Int,
    /** The delta outgrew being a delta and the chunk was baked, so its whole content was rewritten. */
    val baked: Boolean
  )

  /**
   * Bakes a chunk if its delta has grown past the point where keeping it as a delta saves anything.
   *
   * A removal-only delta *is* bounded - a voxel can only be carved down, so the entry count cannot exceed the
   * chunk's volume - so this is not the runaway-growth guard the KDoc here used to claim it was. The bound is
   * simply large and in the wrong direction: a fully worked-out chunk is about a megabyte of packed removals,
   * where that same chunk baked is nearly all air and encodes to a few dozen bytes. So heavily mined ground
   * becomes *cheaper* rather than merely smaller, and its reads skip generation entirely.
   *
   * ### The reference size is cached, and that is the whole point of this shape
   *
   * `ChunkDelta.shouldBake` needs to know what the chunk costs encoded. Computing that here used to mean a full
   * chunk copy and a full run-length encode - 512 kB and half a million bytes scanned - **on every call**, and
   * the call was on the path of every single edited voxel. [encodedSizes] holds it per coordinate instead.
   *
   * Using the *base's* encoded size rather than the merged chunk's is an approximation, and a deliberate one:
   * carving a tunnel through uniform rock breaks up long runs, so the merged chunk encodes *larger* than its
   * base for as long as the chunk is mostly solid. The reference is therefore an underestimate exactly while a
   * chunk is lightly worked, which errs towards baking early - and baking early is merely eager, where baking
   * late means storing many times the chunk as a delta first.
   *
   * @return true when the chunk was baked
   */
  fun compact(chunk: ChunkPos): Boolean {
    val delta = deltas[chunk] ?: return false

    if (!delta.shouldBake(referenceBytesOf(chunk))) return false

    val encoded = RleCodec.encode(delta.mergedOnto(cache.base(chunk)))

    baked.put(bakedKeyOf(chunk), encoded)
    bakedChunks.add(chunk)
    deltas.remove(chunk)
    // The generated base for this coordinate will never be read again.
    cache.evict(chunk)
    encodedSizes.remove(chunk)

    return true
  }

  /**
   * Encoded size of a chunk's generated base, computed once per coordinate.
   *
   * Kept for as long as the chunk has a delta and dropped when it bakes, so this grows with the number of
   * *worked* chunks rather than with the size of the world - the same shape as [deltas] itself.
   */
  private val encodedSizes = HashMap<ChunkPos, Int>()

  private fun referenceBytesOf(chunk: ChunkPos): Int =
    encodedSizes.getOrPut(chunk) { RleCodec.encode(cache.base(chunk)).size }

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
   * Baked blobs are keyed on the coordinate, the seed **and the pipeline version**.
   *
   * The version used to be left out on purpose, on the grounds that a baked chunk no longer depends on the
   * pipeline that produced it - which is true, and is exactly the property that would make baking a
   * migration path for a released world. There is no released world. What the omission bought during
   * development was the opposite of useful: a baked chunk outlived a generator change and then read back
   * indistinguishable from a freshly generated one, so terrain from two different builds sat side by side in
   * one store with nothing able to say which was which.
   *
   * **Put it back when there is something to migrate.** At that point the argument returns intact, and the
   * shape it wants is not this line reverted but a deliberate re-key step that reads at the old version and
   * writes at the new one - which is what a migration is, and is not something a hash function can do for
   * you by being incomplete.
   */
  private fun bakedKeyOf(chunk: ChunkPos) =
    GenRng.hash(config.seed, BAKED_SALT, cache.pipelineVersion, chunk.key())

  override fun toString() = "ChunkStore[${deltas.size} deltas, ${bakedChunks.size} baked]"

  private companion object {
    const val BAKED_SALT = 0x1BA6EDL
  }
}
