package net.bestia.zone.townsfolk.conversation

import net.bestia.zone.text.TextRef

/** What somebody says, and what may be said back. */
data class ConversationNode(
  val speech: TextRef,
  val options: List<ConversationOption>,
)
