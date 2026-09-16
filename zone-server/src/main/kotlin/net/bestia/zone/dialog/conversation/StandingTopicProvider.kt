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
        ConversationKeys.SLOT_TRADE to DialogArg.Token(speaker.tradeToken),
      )
    )
  }

  private companion object {
    const val ABOUT_TOWN = 1
  }
}
