package net.bestia.zone.battle.buff

/**
 * Purely descriptive classification of a [BuffDefinition] (client icon styling, future
 * dispel/cleanse filtering). No gameplay logic branches on this — a debuff is just a buff
 * with negative-magnitude effects, not a separate code path.
 */
enum class BuffPolarity {
  BUFF,
  DEBUFF
}
