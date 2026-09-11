package net.bestia.zone.ai.knowledge

import net.bestia.worldgen.core.EventKind

/**
 * How much of a town's news one trade carries, and which of it.
 *
 * ### This is a bias, never a gate
 *
 * The obvious model is a reach multiplier per trade - an innkeeper hears travellers, so three times as
 * far. It does not work, and not for want of tuning: a multiplier is a *total order*, so whatever the
 * numbers, one trade ends up knowing a superset of what every other trade knows. A player finds that out
 * inside one village and never speaks to anybody else again.
 *
 * So nothing here can put a memory into a town that the town does not have, and nothing here can promise
 * anybody a memory. Both fields only reorder the contest for a memory the settlement already holds - see
 * [TownKnowledge].
 */
data class KnowledgeProfile(
  /**
   * How much this trade carries at all, against a townsperson of no particular curiosity at 1.0.
   *
   * Kept near one deliberately. It is tempting to spread these wide to make trades feel distinct, and
   * the distinctness is supposed to come from [interests] and from the per-person spread instead - a
   * wide curiosity range is a reach gradient wearing a different name.
   */
  val curiosity: Double = 1.0,

  /** Kinds this trade is disproportionately likely to be the one who remembers. */
  val interests: Set<EventKind> = emptySet(),
) {

  fun weightFor(kind: EventKind): Double {
    return if (kind in interests) curiosity * INTEREST else curiosity
  }

  companion object {

    /** Somebody the catalogue says nothing special about. */
    val ORDINARY = KnowledgeProfile()

    /**
     * How much an interest is worth in the ranking.
     *
     * Three, which is enough that a guard usually holds the town's battles and low enough that "usually"
     * stays true - at ten he would hold all of them, and the priest standing next to him would never
     * have anything to say about a siege.
     */
    const val INTEREST = 3.0
  }
}
