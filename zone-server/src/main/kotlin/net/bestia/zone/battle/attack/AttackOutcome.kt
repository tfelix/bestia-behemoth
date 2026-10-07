package net.bestia.zone.battle.attack

/**
 * What became of a swing offered to [AttackExecutionService].
 *
 * The distinction a caller with a standing attack order needs is between the refusals: [NOT_READY] and
 * [OUT_OF_RANGE] are momentary and worth retrying, [IMPOSSIBLE] and [REFUSED] are not and the order has to be
 * given up - a despawned target, a prop that refuses to become a combatant, a target the damage gate shields.
 */
enum class AttackOutcome {
  /** The swing resolved. A miss is still a swing, and still costs the attack delay. */
  SWUNG,
  NOT_READY,
  OUT_OF_RANGE,
  IMPOSSIBLE,

  /** The damage gate turned it away. Ends the order rather than swinging at a shielded target forever. */
  REFUSED
}
