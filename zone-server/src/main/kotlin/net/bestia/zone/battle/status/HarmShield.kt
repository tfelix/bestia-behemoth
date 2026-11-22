package net.bestia.zone.battle.status

/**
 * What harm a status effect turns away while it is active, read by
 * [net.bestia.zone.battle.damage.DamageGate]. A fact of the effect's presence rather than a recalc term, so
 * it holds from the moment the effect is applied.
 */
enum class HarmShield {
  /** Nothing may harm the bearer. */
  ALL,

  /**
   * Nothing may harm the bearer for a while, such as a just captured Empyrite. Unlike [ALL] the bearer is a fair
   * target in itself, so a player who strikes it is told to wait.
   */
  GRACE,

  /** No harm passes between the bearer and anything a player owns, in either direction. */
  PLAYERS,
}
