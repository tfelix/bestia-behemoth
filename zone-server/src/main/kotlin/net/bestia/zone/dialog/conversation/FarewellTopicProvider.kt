package net.bestia.zone.dialog.conversation

import org.springframework.stereotype.Component

/**
 * A way out, always last.
 *
 * Its own provider rather than a special case in the service, because "how a conversation ends" is a
 * thing that will grow - a farewell that differs by how the talk went, or by whether the speaker liked
 * you - and the service should not be where that lives.
 *
 * The first step of that growth is here already: which of the goodbyes somebody uses is theirs, so a
 * town does not sign off with one voice.
 */
@Component
class FarewellTopicProvider(
  private val lines: ConversationLineCatalogue,
) : DialogTopicProvider {

  override val pinned = true

  /** Last. Nothing else may sort after the way out. */
  override val order = Int.MAX_VALUE

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.FAREWELL, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    return listOf(
      ConversationOption(Topics.FAREWELL, Line(ConversationKeys.FAREWELL), OptionKind.END)
    )
  }

  override fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    return ConversationNode(lines.lineFor(speaker, ConversationKeys.GOODBYE), emptyList())
  }
}
