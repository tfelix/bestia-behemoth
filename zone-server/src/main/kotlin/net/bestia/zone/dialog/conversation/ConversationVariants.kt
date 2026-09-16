package net.bestia.zone.dialog.conversation

import net.bestia.worldgen.core.GenRng

/**
 * Which phrasing of a line this person uses, out of the several the client carries.
 *
 * Keyed on the speaker rather than rolled, so asking the same person twice gives the same answer - the
 * property that makes a stateless conversation believable, and the same argument `ConversationService`
 * makes about which topics are offered.
 *
 * [topic] separates the questions a speaker gets asked, so that somebody's greeting and what they say
 * about a battle are not the same draw wearing two hats.
 */
object ConversationVariants {

  /** @return a 1-based index into the [variants] phrasings the key has */
  fun of(speaker: Speaker, topic: Long, variants: Int): Int {
    val roll = GenRng.hashUnit(speaker.seed, topic, VARIANT_SALT)

    return (roll * variants).toInt().coerceIn(0, variants - 1) + 1
  }

  /**
   * Topics that are not a memory.
   *
   * Deliberately far above any event id, so a person's greeting is not drawn with the same key as
   * whatever they remember about event 1.
   */
  const val GREETING = 1_000_001L
  const val TRADE = 1_000_002L

  private const val VARIANT_SALT = 0x7A15L
}
