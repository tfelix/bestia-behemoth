package net.bestia.zone.dialog.conversation

/**
 * Something a townsperson can be asked about.
 *
 * The extension point, and the reason conversation is worth building as anything more than a canned
 * greeting: a shop, a quest board, faction standing and the town's news are all *the same shape* - a few
 * options offered at the root, and a node when one is picked. Each arrives as a Spring component and
 * nothing below has to change.
 *
 * Collected the way `ChatCommand`s already are, by asking for the list in a constructor.
 */
interface DialogTopicProvider {

  /**
   * Whether this provider owns a topic id, asked before [nodeFor].
   *
   * Ids are handed out by [Topics] rather than by each provider, so two providers cannot collide and
   * this question is a range check rather than a lookup.
   */
  fun owns(topicId: Int): Boolean

  /**
   * What this provider offers [speaker] at the root of a conversation, if anything.
   *
   * May be sampled away by the service - only the pinned tier is guaranteed to survive - so a provider
   * must not treat having been asked as having been shown.
   */
  fun rootOptions(speaker: Speaker): List<ConversationOption>

  /** The node for a topic [owns] claimed, or null if the speaker cannot in fact talk about it. */
  fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode?

  /**
   * Whether these options are offered whatever else is competing.
   *
   * A merchant's buy and sell must always be there - an option a player cannot rely on is one they will
   * not learn - while the town's news competes for what is left.
   */
  val pinned: Boolean
    get() = false

  /** Lower sorts first among pinned providers. */
  val order: Int
    get() = 0
}
