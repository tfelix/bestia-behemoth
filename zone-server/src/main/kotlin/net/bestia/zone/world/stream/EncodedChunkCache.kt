package net.bestia.zone.world.stream

import net.bestia.worldgen.core.ChunkPos

/**
 * Encoded chunk payloads by chunk and revision, dropping the one read longest ago once they take more than
 * [capacityBytes].
 *
 * Bounded in bytes rather than in entries because payloads span three orders of magnitude: a uniform chunk is
 * thirteen bytes, a surface chunk a few kilobytes. Tick thread only, like the rest of [ChunkService].
 */
class EncodedChunkCache(private val capacityBytes: Long) {

  private data class Key(val chunk: ChunkPos, val revision: Int)

  /** Access-ordered, so the iterator starts at the payload read longest ago. */
  private val payloads = LinkedHashMap<Key, ChunkService.Encoded>(64, 0.75f, true)

  /** What the held payloads cost, overhead included; never above [capacityBytes]. */
  var bytes = 0L
    private set

  var evictions = 0L
    private set

  val size get() = payloads.size

  init {
    require(capacityBytes > 0) { "The encoded cache must be able to hold something, was $capacityBytes bytes" }
  }

  operator fun get(chunk: ChunkPos, revision: Int): ChunkService.Encoded? {
    return payloads[Key(chunk, revision)]
  }

  fun put(payload: ChunkService.Encoded) {
    val key = Key(payload.chunk, payload.revision)
    val cost = costOf(payload)
    if (cost > capacityBytes) {
      // Keeping an older payload for the key would serve content the caller has just replaced.
      remove(key)
      return
    }

    payloads.put(key, payload)?.let { bytes -= costOf(it) }
    bytes += cost
    evictEldest()
  }

  private fun remove(key: Key) {
    payloads.remove(key)?.let { bytes -= costOf(it) }
  }

  private fun evictEldest() {
    val eldestFirst = payloads.values.iterator()
    while (bytes > capacityBytes) {
      bytes -= costOf(eldestFirst.next())
      eldestFirst.remove()
      evictions++
    }
  }

  private fun costOf(payload: ChunkService.Encoded): Long {
    return payload.payload.size + ENTRY_OVERHEAD_BYTES
  }

  companion object {
    /**
     * Heap an entry costs beyond its payload: the map entry, the key, the [ChunkService.Encoded] and the array
     * header. Counted because most payloads are smaller than this.
     */
    const val ENTRY_OVERHEAD_BYTES = 160L
  }
}
