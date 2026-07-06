package net.bestia.zone.ai.memory

import org.springframework.stereotype.Service

/**
 * Holds the squad-, faction- and world-scoped memory shared between NPCs. This is intentionally a
 * stub for now: the API and the fallback wiring are in place so individual memory can defer to it,
 * but every lookup currently returns nothing. Filling these in later (e.g. a faction that shares
 * enemy sightings) requires no change to the callers.
 */
@Service
class SharedMemoryService {

  fun remember(entry: MemoryEntry) {
    // no-op: shared scopes are not populated yet
  }

  fun recall(
    key: String,
    squadId: String?,
    factionId: String?,
    nowMs: Long = System.currentTimeMillis()
  ): MemoryEntry? {
    return null
  }

  fun recallAll(
    type: MemoryEntry.MemoryType,
    squadId: String?,
    factionId: String?,
    nowMs: Long = System.currentTimeMillis()
  ): List<MemoryEntry> {
    return emptyList()
  }
}
