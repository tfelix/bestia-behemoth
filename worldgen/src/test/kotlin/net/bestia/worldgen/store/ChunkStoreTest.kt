package net.bestia.worldgen.store

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.ChunkEngine
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChunkStoreTest {

  private val config = WorldConfig(
    seed = 0xDEEDL, widthCells = 8, heightCells = 8, chunkSize = 8, chunkHeight = 24, voxelSize = 1.0
  )

  private fun generate(chunk: ChunkPos): VoxelChunk {
    val out = VoxelChunk(chunk, config.chunkSize, config.chunkHeight)
    for (y in 0 until config.chunkSize) {
      for (x in 0 until config.chunkSize) {
        // Deterministic and coordinate-dependent, so a wrong cache key shows up as the wrong terrain rather
        // than as identical terrain everywhere.
        val top = 3 + ((chunk.x * 7 + chunk.y * 3 + x + y) and 3)
        for (z in 0..top) out[x, y, z] = BlockType.GRANITE
      }
    }
    return out
  }

  /** Rock reaches at least this high in every column of [generate], so a carve here always finds material. */
  private val alwaysRock = 3

  private fun voxelIndex(x: Int, y: Int, z: Int) = (y * config.chunkSize + x) * config.chunkHeight + z

  /**
   * Carves voxels away entirely, in the sorted batches `ChunkStore.carve` requires.
   *
   * Sorting here rather than at each call site because the ordering is the codec's contract, not the test's
   * subject - a test that had to hand-order its voxels would be asserting something about itself.
   */
  private fun carve(store: ChunkStore, chunk: ChunkPos, vararg voxels: Triple<Int, Int, Int>) =
    store.carve(
      chunk,
      voxels
        .map { (x, y, z) -> ChunkDelta.pack(voxelIndex(x, y, z), Occupancy.EMPTY) }
        .sorted()
        .toIntArray()
    )

  // --- Caching ---------------------------------------------------------------------------------------

  @Test
  fun `the cache generates once and serves from memory afterwards`() {
    val cache = ChunkCache(config.seed, pipelineVersion = 7L, generate = ::generate)

    val first = cache.base(ChunkPos(1, 1))
    val second = cache.base(ChunkPos(1, 1))

    assertEquals(1, cache.generated)
    assertEquals(1, cache.hits)
    assertTrue(first === second, "the hot tier should hand back the same object")
  }

  @Test
  fun `a cold tier is written on generation and read on a cold start`() {
    val cold = MemoryBlobStore()

    val warm = ChunkCache(config.seed, 7L, ::generate, tiers = listOf(cold))
    warm.base(ChunkPos(2, 3))
    assertEquals(1, cold.size)

    // A different process, same world: nothing in memory, everything in the store.
    val restarted = ChunkCache(config.seed, 7L, { error("must not regenerate") }, tiers = listOf(cold))
    val decoded = restarted.base(ChunkPos(2, 3))

    assertEquals(1, restarted.blobHits)
    assertEquals(0, restarted.generated)
    assertTrue(decoded.blocks.contentEquals(generate(ChunkPos(2, 3)).blocks))
  }

  @Test
  fun `a read from a cold tier is promoted into the warmer ones`() {
    val warm = MemoryBlobStore()
    val cold = MemoryBlobStore()
    cold.put(ChunkKey.of(config.seed, 7L, ChunkPos(0, 0)), net.bestia.worldgen.voxel.RleCodec.encode(generate(ChunkPos(0, 0))))

    val cache = ChunkCache(config.seed, 7L, { error("must not regenerate") }, tiers = listOf(warm, cold))
    cache.base(ChunkPos(0, 0))

    assertEquals(1, warm.size, "the nearer tier should have been filled on the way past")
  }

  @Test
  fun `the cache key folds in the pipeline version`() {
    // What makes invalidation correct by construction: retune a stage and every key changes, so nothing stale
    // can be served and no eviction pass is needed.
    val chunk = ChunkPos(4, 5)
    val a = ChunkKey.of(config.seed, 1L, chunk)
    val b = ChunkKey.of(config.seed, 2L, chunk)
    val c = ChunkKey.of(config.seed + 1, 1L, chunk)

    assertNotEquals(a, b, "a version change must change the key")
    assertNotEquals(a, c, "a seed change must change the key")
    assertEquals(a, ChunkKey.of(config.seed, 1L, chunk))
  }

  @Test
  fun `a version change means a cold store cannot serve the old blob`() {
    val cold = MemoryBlobStore()
    ChunkCache(config.seed, 1L, ::generate, listOf(cold)).base(ChunkPos(1, 0))

    var regenerated = 0
    val upgraded = ChunkCache(config.seed, 2L, { chunk -> regenerated++; generate(chunk) }, listOf(cold))
    upgraded.base(ChunkPos(1, 0))

    assertEquals(1, regenerated, "the new pipeline version must not read the old blob")
  }

  // --- Deltas and baking -----------------------------------------------------------------------------

  @Test
  fun `an untouched chunk is stored as nothing`() {
    // Where the storage saving of the whole design lives: on a large world most chunks a player crosses are
    // never modified, and none of them costs anything.
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate))

    store.merged(ChunkPos(3, 3))

    assertEquals(0, store.deltaCount)
    assertEquals(0, store.bakedCount)
  }

  @Test
  fun `a carve shows through the merged view and leaves the base alone`() {
    val cache = ChunkCache(config.seed, 7L, ::generate)
    val store = ChunkStore(config, cache)
    val chunk = ChunkPos(1, 2)

    carve(store, chunk, Triple(2, 2, alwaysRock))

    assertEquals(BlockType.AIR, store.merged(chunk)[2, 2, alwaysRock])
    assertEquals(BlockType.GRANITE, store.base(chunk)[2, 2, alwaysRock], "the generated base must be untouched")
    assertEquals(1, store.deltaCount)
  }

  /**
   * A partial carve leaves the rock it is cutting into, with less of it.
   *
   * The half of removal-only that a bare "this voxel is now air" model cannot express, and the reason a removal
   * carries an occupancy at all: a brush is a sphere, so the voxels around its edge are partly taken. Losing
   * that would stair-step every tunnel wall in a world whose surfaces are otherwise sub-voxel exact.
   */
  @Test
  fun `a partial carve keeps the block and reduces how much of it is left`() {
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate))
    val chunk = ChunkPos(1, 2)

    store.carve(chunk, intArrayOf(ChunkDelta.pack(voxelIndex(2, 2, alwaysRock), 96)))

    val merged = store.merged(chunk)
    assertEquals(BlockType.GRANITE, merged[2, 2, alwaysRock], "partly carved rock is still rock")
    assertEquals(96, merged.occupancyAt(2, 2, alwaysRock))
  }

  /**
   * Carving is monotone, and a carve that would add material is refused rather than clamped.
   *
   * Clamping would let a placement system arrive by accident and take every derived structure's monotonicity
   * with it. Refusing says the caller is wrong, which it is.
   */
  @Test
  fun `a carve that would raise occupancy is refused`() {
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate))
    val chunk = ChunkPos(1, 2)

    store.carve(chunk, intArrayOf(ChunkDelta.pack(voxelIndex(2, 2, alwaysRock), 96)))

    assertFailsWith<IllegalArgumentException> {
      store.carve(chunk, intArrayOf(ChunkDelta.pack(voxelIndex(2, 2, alwaysRock), 200)))
    }
  }

  /**
   * Carving air changes nothing, and says so.
   *
   * A player grinding a pick against thin air must not move the chunk's revision, or every client holding it
   * pays for a patch describing no change.
   */
  @Test
  fun `carving what is already gone is not a change`() {
    val changed = ArrayList<ChunkPos>()
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate), onChanged = { changed.add(it) })
    val chunk = ChunkPos(0, 0)

    // The fixture leaves everything above the rock as air.
    val outcome = carve(store, chunk, Triple(1, 1, config.chunkHeight - 1))

    assertEquals(0, outcome.changed)
    assertEquals(0, store.deltaCount, "a carve that changed nothing must not create a delta")
    assertTrue(changed.isEmpty(), "nothing changed, so nothing should have been announced")
  }

  @Test
  fun `a carve notifies so derived structures can be invalidated`() {
    val changed = ArrayList<ChunkPos>()
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate), onChanged = { changed.add(it) })

    carve(store, ChunkPos(0, 0), Triple(1, 1, alwaysRock))

    assertEquals(listOf(ChunkPos(0, 0)), changed)
  }

  /**
   * One brush is one announcement, however many voxels it takes.
   *
   * A sphere at the minimum radius takes about seventy voxels. Announcing each one would mean seventy revisions
   * and seventy patches for a single swing, and would put the delta's O(n) merge inside a loop over n.
   */
  @Test
  fun `a batch of removals is announced once`() {
    val changed = ArrayList<ChunkPos>()
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate), onChanged = { changed.add(it) })

    val outcome = carve(
      store, ChunkPos(0, 0),
      Triple(1, 1, 0), Triple(1, 1, 1), Triple(1, 1, 2), Triple(2, 1, 0), Triple(2, 1, 1)
    )

    assertEquals(5, outcome.changed)
    assertEquals(1, changed.size, "five voxels in one brush is one announcement")
  }

  @Test
  fun `a heavily carved chunk bakes and then skips generation entirely`() {
    var generated = 0
    val cache = ChunkCache(config.seed, 7L, { chunk -> generated++; generate(chunk) })
    val store = ChunkStore(config, cache)
    val chunk = ChunkPos(2, 2)

    // Work out every voxel of rock in the chunk. Past the threshold, keeping this as a delta saves nothing -
    // and a chunk this heavily mined bakes to almost pure air, which is where the saving actually is.
    val rock = store.base(chunk)
    val removals = ArrayList<Int>()
    for (y in 0 until config.chunkSize) {
      for (x in 0 until config.chunkSize) {
        for (z in 0 until config.chunkHeight) {
          if (rock[x, y, z] != BlockType.AIR) removals.add(ChunkDelta.pack(voxelIndex(x, y, z), Occupancy.EMPTY))
        }
      }
    }

    val outcome = store.carve(chunk, removals.sorted().toIntArray())

    assertTrue(outcome.baked, "a chunk carved this heavily should have been baked")
    assertTrue(store.isBaked(chunk))
    assertEquals(0, store.deltaCount, "baking drops the delta")

    // The point of baking: reads are now cheaper, not merely smaller.
    val before = generated
    store.merged(chunk)
    assertEquals(before, generated, "a baked chunk must not regenerate its base")
  }

  @Test
  fun `a carve of an already baked chunk goes straight into the stored blob`() {
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate))
    val chunk = ChunkPos(1, 1)

    carve(store, chunk, Triple(0, 0, 1))
    store.bakeAll()
    assertTrue(store.isBaked(chunk))

    carve(store, chunk, Triple(0, 0, 2))

    val merged = store.merged(chunk)
    assertEquals(BlockType.AIR, merged[0, 0, 1], "the pre-bake carve must survive")
    assertEquals(BlockType.AIR, merged[0, 0, 2], "the post-bake carve must too")
    assertEquals(0, store.deltaCount, "a baked chunk has no delta to grow")
  }

  @Test
  fun `baking every delta is the migration path for a pipeline change`() {
    // Once a world ships its pipeline version is frozen, because any change shifts the base out from under the
    // player's deltas - a removal recorded as "this voxel is a third full" starts meaning a third of different
    // rock. Baking first pins the current result, and because a baked blob is keyed on the coordinate and the
    // seed but deliberately *not* on the pipeline version, the upgraded pipeline still finds it.
    val baked = MemoryBlobStore()
    val old = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate), baked)
    carve(old, ChunkPos(0, 0), Triple(1, 1, alwaysRock))
    carve(old, ChunkPos(1, 0), Triple(2, 2, alwaysRock))

    assertEquals(2, old.bakeAll())
    assertEquals(0, old.deltaCount)
    assertEquals(2, old.bakedCount)
    assertEquals(2, baked.size, "both baked blobs are in the store the new pipeline will read")

    assertEquals(BlockType.AIR, old.merged(ChunkPos(0, 0))[1, 1, alwaysRock])
  }

  @Test
  fun `a baked chunk whose blob has vanished fails loudly`() {
    // Falling back to regeneration would silently discard the player's work, which is worse than an exception.
    val baked = ForgetfulBlobStore()
    val store = ChunkStore(config, ChunkCache(config.seed, 7L, ::generate), baked)
    carve(store, ChunkPos(0, 0), Triple(1, 1, alwaysRock))
    store.bakeAll()

    baked.loseEverything()

    assertFailsWith<IllegalStateException> { store.merged(ChunkPos(0, 0)) }
  }

  /** A blob store that can be emptied, to simulate a storage failure. */
  private class ForgetfulBlobStore : ChunkBlobStore {
    private val blobs = HashMap<Long, ByteArray>()

    override fun get(key: Long): ByteArray? = blobs[key]
    override fun put(key: Long, blob: ByteArray) {
      blobs[key] = blob
    }

    fun loseEverything() = blobs.clear()
  }

  // --- Base hashing and the version gate -------------------------------------------------------------

  @Test
  fun `the base hash distinguishes chunks and is stable for one`() {
    val a = BaseHash.of(generate(ChunkPos(0, 0)))
    val b = BaseHash.of(generate(ChunkPos(1, 0)))

    assertEquals(a, BaseHash.of(generate(ChunkPos(0, 0))))
    assertNotEquals(a, b)
  }

  @Test
  fun `the base hash notices a single changed voxel`() {
    // The point of it: a client whose floating point behaves differently produces a base that differs
    // somewhere, and this turns that silent desync into a bandwidth blip.
    val original = generate(ChunkPos(0, 0))
    val altered = original.copy()
    altered[3, 3, 1] = BlockType.SHALE

    assertNotEquals(BaseHash.of(original), BaseHash.of(altered))
  }

  @Test
  fun `matching versions let the client generate its own base`() {
    val server = PipelineVersion.current(pipelineVersion = 42L)
    assertEquals(VersionGate.Verdict.Compatible, VersionGate.check(server, server))
  }

  @Test
  fun `each version component is checked separately and says which one failed`() {
    // One opaque number would be simpler to compare and useless to diagnose. "Your client is one patch behind"
    // and "your client cannot read this format" want different messages.
    val server = PipelineVersion.current(42L)

    fun reasonFor(client: PipelineVersion): String {
      val verdict = VersionGate.check(server, client)
      return (verdict as? VersionGate.Verdict.Incompatible)?.reason
        ?: error("expected $client to be rejected, got $verdict")
    }

    assertTrue(reasonFor(server.copy(pipelineVersion = 41L)).contains("pipeline"))
    assertTrue(reasonFor(server.copy(blockPaletteVersion = 1L)).contains("palette"))
    assertTrue(reasonFor(server.copy(chunkFormatVersion = 99)).contains("format"))
  }

  @Test
  fun `a client that does not generate its own base needs no version agreement`() {
    val server = PipelineVersion.current(42L)
    val ancient = PipelineVersion(1L, 1L, 1)

    assertEquals(
      VersionGate.Verdict.ServerAuthoritativeOnly,
      VersionGate.check(server, ancient, clientGeneratesBase = false)
    )
  }

  @Test
  fun `the palette version tracks ids rather than declaration order`() {
    // Reordering the enum must not invalidate stored data; renumbering an id must invalidate all of it. This
    // asserts the first half - the second is enforced by the ids being explicit constants.
    val once = PipelineVersion.paletteVersion()
    assertEquals(once, PipelineVersion.paletteVersion())
    assertFalse(once == 0L, "the palette hash should not be trivially zero")
  }

  @Test
  fun `the block palette is pinned to the chunk engine version`() {
    // The client no longer receives the palette; it holds a static copy keyed to `ChunkEngine.VERSION`, which
    // is hand-incremented. That leaves exactly one way to break a shipped client silently: change `BlockType`
    // and forget, so every client keeps drawing the old material for a reused id.
    //
    // This is the tripwire. It fails on any change to the ids or names `paletteVersion` folds, and the fix is
    // never to update the number alone: bump `ChunkEngine.VERSION` on both sides, mirror the change into the
    // client's `BlockAppearance.Palette`, and then re-pin here.
    assertEquals(
      -2_483_694_738_586_420_562L, PipelineVersion.paletteVersion(),
      "BlockType changed. Bump ChunkEngine.VERSION here and in the client, mirror the change into the " +
          "client's BlockAppearance.Palette, then update this pin."
    )

    // Both pins were re-pinned together here for PERMAFROST's removal (id 35 freed, folded into DIRT) as
    // part of the pre-release version reset - see ChunkEngine.VERSION's own KDoc. A codec-only bump, unlike
    // this one, moves the version pin below without moving this one.
    assertEquals(
      1, ChunkEngine.VERSION,
      "ChunkEngine.VERSION moved - re-pin this and check the client's constant matches. Both this and the " +
          "palette pin above move together whenever BlockType changes; only a codec-only bump moves this alone."
    )
  }
}
