package net.bestia.zone.battle.status

/**
 * What harm a status effect turns away while it is active, read by
 * [net.bestia.zone.battle.damage.DamageGate]. A fact of the effect's presence rather than a recalc term, so
 * it holds from the moment the effect is applied.
 */
enum class HarmShield {
  /** Nothing may harm the bearer. */
  ALL,
}
