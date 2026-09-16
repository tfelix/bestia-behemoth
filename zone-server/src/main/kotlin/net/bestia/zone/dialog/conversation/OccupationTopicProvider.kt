package net.bestia.zone.dialog.conversation

import net.bestia.zone.dialog.DialogArg
import org.springframework.stereotype.Component

/**
 * What a trade can do for you, as opposed to what it can tell you.
 *
 * Pinned and first, which is the point of the tier: a merchant always has buy and sell, and an option a
 * player cannot rely on is one they never learn to look for. Nothing here is ever sampled away.
 *
 * ### Buying and selling still answer "not yet"
 *
 * Deliberately. The shop they would open exists, but wiring it means a shop panel in the client, and the
 * value of reserving the slot now is that doing so later is an implementation of this interface rather
 * than a change to the node model. The stub is also honest in a way an absent option is not: a player
 * who is told the baker will sell bread eventually knows more than one who finds a baker with nothing to
 * say.
 *
 * Asking what somebody does is the one branch here that answers for real, and it is offered to everybody
 * - a child and a labourer have an answer to that even though they have nothing to sell.
 */
@Component
class OccupationTopicProvider : DialogTopicProvider {

  override val pinned = true
  override val order = 0

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.OCCUPATION, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    val trade = ConversationOption(Topics.OCCUPATION + TRADE, Line(ConversationKeys.TRADE_ASK))
    if (speaker.occupation.businessType == null) {
      return listOf(trade)
    }

    return listOf(
      trade,
      ConversationOption(Topics.OCCUPATION + BUY, Line(ConversationKeys.SHOP_BUY_ASK), OptionKind.ACTION),
      ConversationOption(Topics.OCCUPATION + SELL, Line(ConversationKeys.SHOP_SELL_ASK), OptionKind.ACTION),
    )
  }

  override fun nodeFor(speaker: Speaker, topicId: Int): ConversationNode? {
    return when (Topics.localOf(Topics.OCCUPATION, topicId)) {
      TRADE -> ConversationNode(tradeLine(speaker), emptyList())
      BUY, SELL -> ConversationNode(Line(ConversationKeys.NOT_YET), emptyList())
      else -> null
    }
  }

  /**
   * What this person does, in one of their trade's phrasings.
   *
   * Keyed on the occupation but handed the *trade* as a slot, which is what keeps twenty-seven
   * shopless trades from sounding alike: they are all the `labourer` occupation and so share these
   * phrasings, but a tanner's renders "the tannery" where a baker's renders "the bakery".
   */
  private fun tradeLine(speaker: Speaker): Line {
    val occupation = speaker.occupation
    val variant = ConversationVariants.of(speaker, ConversationVariants.TRADE, occupation.dialog.trade)

    return Line(
      ConversationKeys.TRADE_LINE_PREFIX + occupation.id.uppercase() + "_" + variant,
      mapOf(ConversationKeys.SLOT_TRADE to DialogArg.Token(speaker.tradeToken)),
    )
  }

  private companion object {
    const val TRADE = 1
    const val BUY = 2
    const val SELL = 3
  }
}
