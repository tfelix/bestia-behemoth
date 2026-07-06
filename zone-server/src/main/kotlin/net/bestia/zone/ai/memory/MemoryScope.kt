package net.bestia.zone.ai.memory

/**
 * The visibility scope a [MemoryEntry] belongs to. Lookups fall back through the scopes in the order
 * [INDIVIDUAL] -> [SQUAD] -> [FACTION] -> [WORLD], so an NPC prefers its own knowledge but can lean
 * on shared knowledge when it has none of its own.
 */
enum class MemoryScope {
  INDIVIDUAL,
  SQUAD,
  FACTION,
  WORLD
}
