package net.bestia.zone.questgen.offer

import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.text.TextRef
import net.bestia.zone.townsfolk.conversation.Asker
import net.bestia.zone.townsfolk.conversation.ConversationNode
import net.bestia.zone.townsfolk.conversation.ConversationOption
import net.bestia.zone.townsfolk.conversation.DialogTopicProvider
import net.bestia.zone.townsfolk.conversation.OptionKind
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.townsfolk.conversation.Topics
import org.springframework.stereotype.Component

/**
 * A townsperson a quest starts with speaks of it and lets the player take it. Whoever the town will not talk to
 * at all never gets this far: the cold shoulder comes before every topic.
 */
@Component
class QuestTopicProvider(
  private val offers: QuestOffers,
  private val sessions: ConnectionInfoService,
  private val jobs: AsyncJobExecutor,
  private val out: OutMessageProcessor,
) : DialogTopicProvider {

  override val pinned = true

  override val order = 3

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.QUEST, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    if (offerOf(speaker) == null) {
      return emptyList()
    }

    return listOf(ConversationOption(Topics.QUEST + TELL, TextRef(ASK_KEY)))
  }

  override fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    val offer = offerOf(speaker) ?: return null
    val args = offer.plan.title.args

    return when (Topics.localOf(Topics.QUEST, topicId)) {
      TELL -> ConversationNode(
        TextRef(offer.plan.title.key.replace(TITLE_SUFFIX, OFFER_SUFFIX), args),
        listOf(ConversationOption(Topics.QUEST + ACCEPT, TextRef(ACCEPT_KEY, args), OptionKind.ACTION)),
      )

      ACCEPT -> {
        accept(asker, offer)
        ConversationNode(TextRef(ACCEPTED_KEY, args), emptyList())
      }

      else -> null
    }
  }

  /**
   * The conversation runs on the tick, so the log is written by a job. The new quest reaches the player as a log
   * update, a refusal as an error.
   */
  private fun accept(asker: Asker, offer: QuestOffers.Offer) {
    val masterId = sessions.activeMasters()[asker.accountId] ?: return

    jobs.submit(masterId) {
      offers.accept(masterId, offer.planId)?.let { refusal -> out.sendToPlayer(asker.accountId, OperationErrorSMSG(refusal)) }
    }
  }

  private fun offerOf(speaker: Speaker): QuestOffers.Offer? {
    return offers.at(WorldTarget.townsfolk(speaker.identity))
  }

  private companion object {
    const val TELL = 1
    const val ACCEPT = 2

    const val ASK_KEY = "QUESTGEN_TOPIC_ASK"
    const val ACCEPT_KEY = "QUESTGEN_ACCEPT"
    const val ACCEPTED_KEY = "QUESTGEN_ACCEPTED"

    /** The giver's own words about a quest are the title key's twin, as the log's longer text is. */
    const val TITLE_SUFFIX = "_TITLE"
    const val OFFER_SUFFIX = "_OFFER"
  }
}
