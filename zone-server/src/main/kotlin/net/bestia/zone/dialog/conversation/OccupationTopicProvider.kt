package net.bestia.zone.dialog.conversation

import org.springframework.stereotype.Component

/**
 * What a trade can do for you, as opposed to what it can tell you.
 *
 * Pinned and first, which is the point of the tier: a merchant always has buy and sell, and an option a
 * player cannot rely on is one they never learn to look for. Nothing here is ever sampled away.
 *
 * ### Everything answers "not yet"
 *
 * Deliberately. The shop this would open exists, but wiring it means a shop panel in the client, and the
 * value of reserving the slot now is that doing so later is an implementation of this interface rather
 * than a change to the node model. The stub is also honest in a way an absent option is not: a player
 * who is told the baker will sell bread eventually knows more than one who finds a baker with nothing to
 * say.
 */
@Component
class OccupationTopicProvider : DialogTopicProvider {

  override val pinned = true
  override val order = 0

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.OCCUPATION, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    if (speaker.occupation.businessType == null) {
      return emptyList()
    }

    return listOf(
      ConversationOption(Topics.OCCUPATION + BUY, Line(ConversationKeys.SHOP_BUY_ASK), OptionKind.ACTION),
      ConversationOption(Topics.OCCUPATION + SELL, Line(ConversationKeys.SHOP_SELL_ASK), OptionKind.ACTION),
    )
  }

  override fun nodeFor(speaker: Speaker, topicId: Int): ConversationNode? {
    return when (Topics.localOf(Topics.OCCUPATION, topicId)) {
      BUY, SELL -> ConversationNode(Line(ConversationKeys.NOT_YET), emptyList())
      else -> null
    }
  }

  private companion object {
    const val BUY = 1
    const val SELL = 2
  }
}
