package net.bestia.zone.dialog.conversation

/**
 * One thing the player may say next.
 *
 * [topicId] is a topic and not a position in a list, which is what lets the server keep no conversation
 * state: there is no cursor to remember, so a reconnect costs nothing and a stale click resolves to the
 * same thing it would have before.
 */
data class ConversationOption(
  val topicId: Int,
  val line: Line,
  val kind: OptionKind = OptionKind.TALK,
)
