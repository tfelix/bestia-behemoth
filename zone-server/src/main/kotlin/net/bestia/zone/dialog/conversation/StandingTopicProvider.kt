package net.bestia.zone.dialog.conversation

import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.ai.knowledge.ChronicleNames
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/**
 * The things anybody can be asked, wherever they live: where this is, and who they are.
 *
 * Pinned, because a conversation that sometimes offers nothing to say is a conversation a player stops
 * opening. It is also the floor the sampling relies on - a speaker with no memories and no trade still
 * has a root worth showing.
 */
@Component
class StandingTopicProvider(
  private val worldService: WorldService,
) : DialogTopicProvider {

  override val pinned = true
  override val order = 10

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.STANDING, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    return listOf(
      ConversationOption(Topics.STANDING + ABOUT_TOWN, Line(ConversationKeys.ABOUT_TOWN_ASK))
    )
  }

  override fun nodeFor(speaker: Speaker, topicId: Int): ConversationNode? {
    return when (Topics.localOf(Topics.STANDING, topicId)) {
      ABOUT_TOWN -> ConversationNode(aboutTown(speaker), emptyList())
      else -> null
    }
  }

  private fun aboutTown(speaker: Speaker): Line {
    val chronicle = worldService.generated.world.chronicle
    val town = ChronicleNames.placeOf(chronicle, speaker.settlement)

    return Line(
      ConversationKeys.ABOUT_TOWN,
      mapOf(
        ConversationKeys.SLOT_TOWN to DialogArg.Name(town.orEmpty()),
        ConversationKeys.SLOT_TRADE to DialogArg.Token(tradeTokenOf(speaker)),
      )
    )
  }

  /**
   * The speaker's trade as a key rather than its label.
   *
   * `Occupation.label` is an English word out of a yml file, and putting one into a line would make the
   * sentence around it untranslatable for the sake of one noun.
   *
   * The household's own trade first, and the occupation only as a fallback: thirty trades collapse into
   * six occupations, so a baker, a mason and a tanner were all saying "I keep the work here".
   */
  private fun tradeTokenOf(speaker: Speaker): String {
    return TRADE_PREFIX + (speaker.business ?: speaker.occupation.id).uppercase()
  }

  private companion object {
    const val ABOUT_TOWN = 1
    const val TRADE_PREFIX = "TRADE_"
  }
}
