package net.bestia.zone.dialog.conversation

/**
 * Where each provider's topic ids live.
 *
 * Handed out centrally rather than left to each provider, for two reasons. Two providers cannot collide
 * on an id, which would be a bug a player finds by asking a merchant about the weather and being shown a
 * price list. And a provider's `owns` becomes a range check rather than a lookup, so answering "whose
 * topic is this" costs nothing.
 */
object Topics {

  /**
   * How many ids each provider gets.
   *
   * Comfortably past the size of a pruned chronicle, which is the largest local id anybody uses - the
   * knowledge provider's local id is an event id, and a thousand-year log keeps a few thousand events.
   */
  const val STRIDE = 100_000

  /**
   * Back to the top of the conversation.
   *
   * Owned by the service rather than by a provider, because it is the one topic that is not about
   * anything - and because every leaf needs it, so no provider should have to remember to offer it.
   */
  const val ROOT = 0

  const val STANDING = 1 * STRIDE
  const val KNOWLEDGE = 2 * STRIDE
  const val OCCUPATION = 3 * STRIDE
  const val SMALL_TALK = 4 * STRIDE
  const val RUMOUR = 5 * STRIDE
  const val FAREWELL = 6 * STRIDE

  fun owns(base: Int, topicId: Int): Boolean {
    return topicId >= base && topicId < base + STRIDE
  }

  fun localOf(base: Int, topicId: Int): Int {
    return topicId - base
  }
}
