package net.bestia.zone.ai.memory

/**
 * The per-NPC blackboard. Stores this individual's own memories and, on a miss, falls back to the
 * shared scopes (squad -> faction -> world) via [shared]. The shared service is currently stubbed
 * (returns nothing), so today the fallback is effectively a no-op, but the seam is in place.
 */
class IndividualMemory(
  private val shared: SharedMemoryService? = null,
  private val squadId: String? = null,
  private val factionId: String? = null
) : Blackboard {

  private val entries = mutableMapOf<String, MemoryEntry>()

  override fun remember(entry: MemoryEntry) {
    entries[entry.key] = entry
  }

  override fun recall(key: String, nowMs: Long): MemoryEntry? {
    val own = entries[key]?.takeUnless { it.isExpired(nowMs) }
    if (own != null) {
      return own
    }

    return shared?.recall(key, squadId, factionId, nowMs)
  }

  override fun recallAll(type: MemoryEntry.MemoryType, nowMs: Long): List<MemoryEntry> {
    val own = entries.values.filter { it.type == type && !it.isExpired(nowMs) }
    val sharedEntries = shared?.recallAll(type, squadId, factionId, nowMs) ?: emptyList()

    return own + sharedEntries
  }

  override fun forgetExpired(nowMs: Long) {
    entries.values.removeIf { it.isExpired(nowMs) }
  }
}
