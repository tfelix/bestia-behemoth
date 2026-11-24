package net.bestia.zone.battle.gear

/**
 * What a tool essence improves while its tool is in hand, in percent. The essences in the tool fill it, and the work
 * the tool does reads it.
 */
enum class ToolTerm(val maxPercent: Int = Int.MAX_VALUE) {
  /** Less time per pickaxe swing. */
  MINING_TIME_CUT(MAX_CUT_PERCENT),

  /** More felling damage per blow on a tree. */
  FELLING_DAMAGE,

  /** Less time per metre dug. */
  DIGGING_TIME_CUT(MAX_CUT_PERCENT),

  /** Less waiting for a bite. */
  BITE_WAIT_CUT(MAX_CUT_PERCENT),

  /** Chance of a second piece of mined ore, never gold. */
  SECOND_ORE_CHANCE,

  /** Chance that a use wears the tool not at all. */
  WEAR_CUT(MAX_CUT_PERCENT),
}

/** Four Diamond essences give 180%; work never becomes free. */
private const val MAX_CUT_PERCENT = 75
