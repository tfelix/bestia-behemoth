package net.bestia.zone.battle.status

/**
 * Purely descriptive classification of a [StatusEffectDefinition] (client icon styling, future
 * dispel/cleanse filtering). No gameplay logic branches on this — a debuff is just a status effect
 * with negative-magnitude effects, not a separate code path.
 */
enum class StatusEffectSource {
  BUFF,
  DEBUFF,
  EQUIP
}
