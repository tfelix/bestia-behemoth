package net.bestia.zone.dialog.conversation

/** What somebody says, and what may be said back. */
data class ConversationNode(
  val speech: Line,
  val options: List<ConversationOption>,
)
