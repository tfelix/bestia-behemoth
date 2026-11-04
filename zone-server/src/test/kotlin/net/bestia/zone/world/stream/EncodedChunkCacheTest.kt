package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.world.stream.EncodedChunkCache.Companion.ENTRY_OVERHEAD_BYTES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class EncodedChunkCacheTest {

  private fun payload(x: Int, size: Int = 100, revision: Int = 0): ChunkService.Encoded {
    return ChunkService.Encoded(
      chunk = ChunkPos(x, 0, 0),
      revision = revision,
      compression = ChunkDataSMSG.Compression.NONE,
      payload = ByteArray(size),
      baseHash = 0L,
      encodedBytes = size
    )
  }

  /** Room for exactly [count] payloads of [size] bytes. */
  private fun cacheFor(count: Int, size: Int = 100): EncodedChunkCache {
    return EncodedChunkCache(count * (size + ENTRY_OVERHEAD_BYTES))
  }

  @Test
  fun `a payload is served back by chunk and revision`() {
    val cache = cacheFor(count = 2)
    val base = payload(1)
    val edited = payload(1, revision = 3)

    cache.put(base)
    cache.put(edited)

    assertSame(base, cache[ChunkPos(1, 0, 0), 0])
    assertSame(edited, cache[ChunkPos(1, 0, 0), 3])
    assertNull(cache[ChunkPos(1, 0, 0), 1])
  }

  @Test
  fun `the payload read longest ago is evicted first`() {
    val cache = cacheFor(count = 2)
    cache.put(payload(1))
    cache.put(payload(2))

    // A read makes 1 the most recent, so 2 is the one to go.
    cache[ChunkPos(1, 0, 0), 0]
    cache.put(payload(3))

    assertNotNull(cache[ChunkPos(1, 0, 0), 0])
    assertNull(cache[ChunkPos(2, 0, 0), 0])
    assertNotNull(cache[ChunkPos(3, 0, 0), 0])
    assertEquals(1, cache.evictions)
  }

  @Test
  fun `one large payload evicts as many small ones as it needs`() {
    val cache = cacheFor(count = 4)
    repeat(4) { cache.put(payload(it)) }

    cache.put(payload(9, size = 250))

    assertEquals(3, cache.size, "the large payload and the two newest small ones")
    assertNull(cache[ChunkPos(1, 0, 0), 0])
    assertNotNull(cache[ChunkPos(2, 0, 0), 0])
    assertEquals(2, cache.evictions)
  }

  @Test
  fun `replacing a payload counts only the new one`() {
    val cache = cacheFor(count = 2)
    cache.put(payload(1))

    cache.put(payload(1, size = 40))

    assertEquals(1, cache.size)
    assertEquals(40 + ENTRY_OVERHEAD_BYTES, cache.bytes)
  }

  @Test
  fun `a payload larger than the whole cache is not kept, and neither is the one it replaced`() {
    val cache = cacheFor(count = 1)
    cache.put(payload(1))

    cache.put(payload(1, size = 500))

    assertNull(cache[ChunkPos(1, 0, 0), 0])
    assertEquals(0, cache.bytes)
  }
}
