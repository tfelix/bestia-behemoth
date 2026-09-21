package net.bestia.zone.battle.skill

/**
 * What became of a swing offered to [AttackExecutionService].
 *
 * The distinction a caller with a standing attack order needs is between the two refusals: [NOT_READY] and
 * [OUT_OF_RANGE] are momentary and worth retrying, [IMPOSSIBLE] never resolves and the order has to be given
 * up - a despawned target, or a prop that refuses to become a combatant at all.
 */
enum class AttackOutcome {
  /** The swing resolved. A miss is still a swing, and still costs the attack delay. */
  SWUNG,
  NOT_READY,
  OUT_OF_RANGE,
  IMPOSSIBLE
}
