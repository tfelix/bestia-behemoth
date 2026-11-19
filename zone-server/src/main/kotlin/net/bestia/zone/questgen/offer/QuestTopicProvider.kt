package net.bestia.zone.questgen.offer

import net.bestia.worldgen.pop.Kinship
import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.questgen.ending.QuestReturns
import net.bestia.zone.treasure.Relics
import net.bestia.zone.util.EntityId
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.text.TextRef
import net.bestia.zone.text.TextSubject
import net.bestia.zone.text.WorldTexts
import net.bestia.zone.townsfolk.conversation.Asker
import net.bestia.zone.townsfolk.conversation.ConversationNode
import net.bestia.zone.townsfolk.conversation.ConversationOption
import net.bestia.zone.townsfolk.conversation.DialogTopicProvider
import net.bestia.zone.townsfolk.conversation.OptionKind
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.townsfolk.conversation.Topics
import org.springframework.stereotype.Component

/**
 * A townsperson a quest starts with speaks of it and lets the player take it. In a town that wants its artifact
 * back, the heads of the households take it from the master who brings it. Whoever the town will not talk to at
 * all never gets this far: the cold shoulder comes before every topic.
 */
@Component
class QuestTopicProvider(
  private val offers: QuestOffers,
  private val sessions: ConnectionInfoService,
  private val jobs: AsyncJobExecutor,
  private val out: OutMessageProcessor,
  private val returns: QuestReturns,
  private val relics: Relics,
  private val world: WorldView,
  private val texts: WorldTexts,
  private val masters: MasterEntityResolver = MasterEntityResolver(sessions),
) : DialogTopicProvider {

  override val pinned = true

  override val order = 3

  override fun owns(topicId: Int): Boolean {
    return Topics.owns(Topics.QUEST, topicId)
  }

  override fun rootOptions(speaker: Speaker): List<ConversationOption> {
    val tell = offerOf(speaker)?.let { ConversationOption(Topics.QUEST + TELL, TextRef(ASK_KEY)) }
    val bringHome = awaitedBy(speaker)?.let {
      ConversationOption(Topics.QUEST + BRING_HOME, TextRef(BRING_HOME_KEY, it.plan.title.args), OptionKind.ACTION)
    }

    return listOfNotNull(tell, bringHome)
  }

  override fun nodeFor(asker: Asker, speaker: Speaker, topicId: Int): ConversationNode? {
    val local = Topics.localOf(Topics.QUEST, topicId)
    if (local == BRING_HOME) {
      return awaitedBy(speaker)?.let { takeBack(asker, it) }
    }

    val offer = offerOf(speaker) ?: return null

    return when (local) {
      TELL -> ConversationNode(
        texts.attach(lineOf(offer, OFFER_SUFFIX), TextSubject.questOffer(offer.planId)),
        listOf(ConversationOption(Topics.QUEST + ACCEPT, lineOf(offer, ACCEPT_SUFFIX), OptionKind.ACTION)),
      )

      ACCEPT -> {
        accept(asker, offer)
        ConversationNode(lineOf(offer, ACCEPTED_SUFFIX), emptyList())
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

  /**
   * The town thanks whoever carries its artifact, and waits on for anybody else. The bag is read here on the
   * tick; taking the artifact out of it is a job, and its refusal reaches the player as an error.
   */
  private fun takeBack(asker: Asker, awaited: QuestOffers.Offer): ConversationNode {
    val args = awaited.plan.title.args
    val masterEntityId = masters.selectedMasterEntityOf(asker.accountId)
    val carried = masterEntityId?.let { relicCarriedBy(it, awaited.plan.seed.subject.index) }
    val master = masterEntityId?.let { id -> world.read { get(id, Master::class) } }
    if (masterEntityId == null || carried == null || master == null) {
      return ConversationNode(TextRef(STILL_WAITING_KEY, args), emptyList())
    }

    val doer = LivingChronicle.Doer(master.masterId, master.name)
    jobs.submit(doer.masterId) {
      val refusal = returns.handOver(doer, awaited.planId, carried)
      if (refusal != null) {
        out.sendToPlayer(asker.accountId, OperationErrorSMSG(refusal))
      } else {
        world.post { get(masterEntityId, Inventory::class)?.take(carried, 1) }
      }
    }

    return ConversationNode(TextRef(HOME_AGAIN_KEY, args), emptyList())
  }

  /** The id of the loose copy of the artifact in the master's bag, or null. Gear being worn is not handed over. */
  private fun relicCarriedBy(masterEntityId: EntityId, artifact: Int): Long? {
    return world.read {
      get(masterEntityId, Inventory::class)?.getItems()
        ?.firstOrNull { !it.equipped && relics.artifactOf(it.uniqueId) == artifact }
        ?.uniqueId
    }
  }

  /** What is said about a quest is worded per kind of quest: each line is a twin of the title's key. */
  private fun lineOf(offer: QuestOffers.Offer, suffix: String): TextRef {
    val title = offer.plan.title

    return TextRef(title.key.replace(TITLE_SUFFIX, suffix), title.args)
  }

  private fun offerOf(speaker: Speaker): QuestOffers.Offer? {
    return offers.at(WorldTarget.townsfolk(speaker.identity))
  }

  /** One voice per house speaks for the town, so not every child offers to take the artifact. */
  private fun awaitedBy(speaker: Speaker): QuestOffers.Offer? {
    return offers.broughtTo(speaker.settlement).takeIf { speaker.member.kinship == Kinship.HEAD }
  }

  private companion object {
    const val TELL = 1
    const val ACCEPT = 2
    const val BRING_HOME = 3

    const val ASK_KEY = "QUESTGEN_TOPIC_ASK"
    const val BRING_HOME_KEY = "QUESTGEN_BRING_HOME"
    const val STILL_WAITING_KEY = "QUESTGEN_STILL_WAITING"
    const val HOME_AGAIN_KEY = "QUESTGEN_HOME_AGAIN"

    const val TITLE_SUFFIX = "_TITLE"
    const val OFFER_SUFFIX = "_OFFER"
    const val ACCEPT_SUFFIX = "_ACCEPT"
    const val ACCEPTED_SUFFIX = "_ACCEPTED"
  }
}
