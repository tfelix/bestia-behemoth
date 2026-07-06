package net.bestia.zone.ai.memory

/**
 * Read/write access to an NPC's knowledge. A concrete blackboard resolves lookups with a fallback
 * chain across [MemoryScope]s (individual first, then the shared scopes), so callers do not have to
 * know where a fact came from.
 */
interface Blackboard {

  /**
   * Remember [entry], overwriting any existing entry with the same key in its scope.
   */
  fun remember(entry: MemoryEntry)

  /**
   * The freshest non-expired entry for [key], searching the individual scope first and then falling
   * back to the shared scopes, or null if nothing is known.
   */
  fun recall(key: String, nowMs: Long = System.currentTimeMillis()): MemoryEntry?

  /**
   * All non-expired entries of the given [type] across the visible scopes.
   */
  fun recallAll(type: MemoryEntry.MemoryType, nowMs: Long = System.currentTimeMillis()): List<MemoryEntry>

  /**
   * Drop entries whose expiry has passed.
   */
  fun forgetExpired(nowMs: Long = System.currentTimeMillis())
}
