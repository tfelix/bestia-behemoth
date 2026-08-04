package net.bestia.worldgen.store

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.RleCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a chunk and a delta actually cost, asserted rather than assumed.
 *
 * Storage for this world is proportional to what players change, not to how big the world is, so these few
 * numbers are the entire storage model. A codec change that quietly quintupled the per-chunk cost would not
 * break a single other test - every one of them would still decode what it encoded - and the bill would show up
 * months later as an object store invoice nobody could account for. The bounds are generous; they exist to
 * catch a regression of a factor, not of a few percent.
 */
class StorageBudgetTest {

  private val world by lazy {
    StandardWorld.build(StandardWorld.demoConfig().copy(widthCells = 160, heightCells = 160))
  }

  @Test
  fun `run length encoding takes a chunk to a small fraction of its raw voxels`() {
    val land = world.materializer.materializeSurface(1600, 1600)
    val raw = land.volume * 2
    val encoded = RleCodec.encode(land).size

    // Measured at about thirty-six times on a surface chunk, which is the worst case: it has the most material
    // boundaries and the only partial voxels.
    assertTrue(
      encoded < raw / 10,
      "a surface chunk encoded to $encoded of $raw raw bytes - less than a tenth was expected"
    )
  }

  @Test
  fun `a uniform chunk costs almost nothing and deflating it does not cost more`() {
    // There are far more of these than there are surface chunks: everything below the ground and everything
    // above it. If the common case were not nearly free the world would not be storable at all.
    val land = world.materializer.materializeSurface(1600, 1600)
    val deep = world.materializer.materialize(ChunkPos(1600, 1600, land.chunk.z - 3))
    val encoded = RleCodec.encode(deep)

    assertTrue(encoded.size < 64, "an underground chunk encoded to ${encoded.size} bytes")

    // Deflate has a fixed overhead and loses on inputs this small, so the store must keep the smaller of the
    // two. Compressing unconditionally would make the most common chunk in the world bigger.
    val inner = MemoryBlobStore()
    DeflatedBlobStore(inner).put(1L, encoded)
    assertTrue(
      inner.get(1L)!!.size <= encoded.size + 1,
      "deflating a ${encoded.size} byte chunk grew it to ${inner.get(1L)!!.size}"
    )
  }

  @Test
  fun `deflating a surface chunk saves several times over what run length encoding already did`() {
    // RLE removes the long vertical runs; deflate removes the repetition *between* runs, which RLE cannot see.
    // Measured at about four and a half times on top.
    val land = world.materializer.materializeSurface(1600, 1600)
    val encoded = RleCodec.encode(land)

    val inner = MemoryBlobStore()
    val store = DeflatedBlobStore(inner)
    store.put(7L, encoded)

    val stored = inner.get(7L)!!.size
    assertTrue(
      stored < encoded.size / 2,
      "deflate took a ${encoded.size} byte chunk to $stored bytes - at least half was expected"
    )
    assertTrue(store.get(7L)!!.contentEquals(encoded), "the blob did not survive the round trip")
  }

  @Test
  fun `a delta stops being cheaper than the chunk it modifies long before it covers it`() {
    val land = world.materializer.materializeSurface(1600, 1600)
    val config = world.config
    val reference = RleCodec.encode(land).size

    fun deltaOf(removals: Int): ChunkDelta {
      val delta = ChunkDelta(land.chunk, config.chunkSize, config.chunkHeight)
      val packed = ArrayList<Int>(removals)
      outer@ for (y in 0 until config.chunkSize) {
        for (x in 0 until config.chunkSize) {
          for (z in 0 until config.chunkHeight) {
            packed.add(ChunkDelta.pack((y * config.chunkSize + x) * config.chunkHeight + z, Occupancy.EMPTY))
            if (packed.size >= removals) break@outer
          }
        }
      }
      delta.carveAll(packed.toIntArray())
      return delta
    }

    // A gallery is a few thousand voxels and stays a delta; a worked-out orebody does not.
    assertTrue(!deltaOf(1_000).shouldBake(reference), "a thousand removals should still be a delta")
    assertTrue(deltaOf(40_000).shouldBake(reference), "forty thousand removals should be baked")

    // And the size test is what fires, not the coverage test: the crossover is a few percent of the chunk's
    // voxels, so waiting for thirty percent would mean storing several times the chunk as a delta first. This
    // ordering is why `ChunkStore.compact` caches the reference size rather than dropping the size test - the
    // test is the trigger, and recomputing it per removal is what used to make carving expensive.
    val crossover = deltaOf(40_000)
    assertTrue(
      crossover.coverage < ChunkDelta.BAKE_COVERAGE,
      "coverage reached ${crossover.coverage} before the size test fired, so the size test is redundant"
    )
  }

  /**
   * A removal costs less on the wire and in storage than an edit that also had to name a block.
   *
   * The number this file exists to defend: storage is proportional to what players remove, so the per-removal
   * cost *is* the storage model. Dropping the block id is sound rather than a saving - under removal-only the
   * block is derivable from the base, so it is not being omitted, it is being recomputed.
   */
  @Test
  fun `a removal costs three bytes rather than five`() {
    assertTrue(
      ChunkDelta.BYTES_PER_REMOVAL < 5,
      "a removal costs ${ChunkDelta.BYTES_PER_REMOVAL} bytes, which is no better than an edit did"
    )

    val delta = ChunkDelta(ChunkPos(0, 0), 32, 256)
    delta.carveAll(IntArray(1_000) { ChunkDelta.pack(it, Occupancy.EMPTY) })

    assertEquals(1_000 * ChunkDelta.BYTES_PER_REMOVAL, delta.estimatedBytes())
  }

  @Test
  fun `an untouched chunk is stored as nothing at all`() {
    // The whole storage model in one assertion. The base is a pure function of seed, pipeline version and
    // coordinate, so a chunk nobody has changed does not need to exist anywhere - and on a world of this size
    // that is all but every chunk.
    val blobs = MemoryBlobStore()
    val cache = ChunkCache(
      seed = world.config.seed,
      pipelineVersion = world.world.pipelineVersion,
      generate = { world.materializer.materialize(it) },
      tiers = emptyList(),
      hotCapacity = 4
    )
    val store = ChunkStore(world.config, cache, baked = blobs)

    val chunk = ChunkPos(1600, 1600, 0)
    store.merged(chunk)
    store.merged(chunk)

    assertEquals(0, blobs.size, "reading a chunk nobody edited wrote something to durable storage")
  }
}
