package net.bestia.zone.ai.core.state

/**
 * An appetite that rises on its own until something spends it - hunger, tiredness, boredom.
 *
 * Rates are per **in-game hour**, which is the unit the behaviour is actually authored in: "hungry in eight
 * hours" stays true if the world clock is ever retuned, where a per-real-second figure silently becomes a
 * different creature. `AiDriveSystem` converts once per sweep.
 *
 * That difference is not cosmetic. A day here lasts eight real hours, so a mob tuned to get peckish in
 * three real minutes and a townsperson who eats at noon are two orders of magnitude apart, and expressing
 * both in the same unit is what stops one domain's tuning reading as a mistake in the other's.
 */
class Drive(
  val key: StateKey<Int>,
  val perGameHour: Float,
  /** Defaults to [perGameHour]: lying down does not make you less bored, and it certainly does not feed you. */
  val whileSleepingPerGameHour: Float = perGameHour,
) {

  /**
   * Where the sub-integer remainder lives, so a rate slower than one point per tick still accumulates.
   *
   * Derived from the drive's own name rather than looked up, which is what lets a domain add a drive
   * without also having to register it somewhere else - the previous map threw for any key missing from it.
   */
  val fractionKey: StateKey<Float> = StateKey("${key.name}Fraction", retain = Blackboard.PERMANENT)
}
