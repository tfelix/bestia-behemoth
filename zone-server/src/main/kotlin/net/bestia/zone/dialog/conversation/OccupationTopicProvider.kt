package net.bestia.zone.dialog.conversation

import net.bestia.zone.dialog.DialogArg
import org.springframework.stereotype.Component

/**
 * What somebody does for a living.
 *
 * Pinned and first, and offered to everybody: a child and a labourer have an answer to this even though
 * they have nothing to sell. Buying and selling are [ShopTopicProvider]'s, which is what the slot
 * reserved here was always for.
 */
@Component
class OccupationTopicProvider : DialogTopicProvider {

  override val pinned = true
  override val order = 0

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.OCCUPATION, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    return listOf(ConversationOption(Topics.OCCUPATION + TRADE, Line(ConversationKeys.TRADE_ASK)))
  }

  override fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    return when (Topics.localOf(Topics.OCCUPATION, topicId)) {
      TRADE -> ConversationNode(tradeLine(speaker), emptyList())
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
  }
}
