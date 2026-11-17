package net.bestia.zone.blackboard

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.blackboard.persistence.BlackboardPosting
import net.bestia.zone.blackboard.persistence.BlackboardPostingRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestDraft
import net.bestia.zone.quest.QuestObjective
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestReward
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.QuestTextArg
import net.bestia.zone.quest.QuestsDeletedEvent
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/**
 * The money side of blackboard quests: a poster pays the whole pay and the fee up front, the pay waits on the
 * posting, and what nobody earned goes back by post when the quest is withdrawn or runs out. Every board shares
 * one list. Off the tick: every call reads or writes the database.
 */
@Service
class BlackboardService(
  private val quests: QuestService,
  private val postings: BlackboardPostingRepository,
  private val inventoryService: InventoryService,
  private val commodityItems: CommodityItems,
  private val itemTemplates: ItemTemplateRegistry,
  private val itemRepository: ItemRepository,
  private val parcels: ParcelService,
  private val reserve: WorldReserve,
  private val levelHint: QuestLevelHint,
  private val world: WorldView,
  private val out: OutMessageProcessor,
  private val transactions: TransactionTemplate,
  private val config: BlackboardConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** Acts on [request] and answers with the board as it now stands, and the refusal first if there was one. */
  fun handle(visit: BoardVisit, request: BlackboardRequest) {
    val refusal = when (request) {
      BlackboardRequest.Open -> null
      is BlackboardRequest.Post -> post(visit, request)
      is BlackboardRequest.Withdraw -> withdraw(visit.masterId, request.questId)
    }
    refusal?.let { out.sendToPlayer(visit.accountId, OperationErrorSMSG(it)) }
    out.sendToPlayer(visit.accountId, boardFor(visit))
  }

  fun post(visit: BoardVisit, request: BlackboardRequest.Post): OpError? {
    val coinId = commodityItems.coinItemId() ?: return OpError.REQUEST_REFUSED
    val feePercent = config.feePercentByDays[request.days]
    // The client only offers what these allow, so a refusal here is a hand-made packet.
    val isPossible = feePercent != null && request.amount in 1..config.maxAmount &&
      request.coinsEach in 1..config.maxCoinsEach && request.itemId != coinId &&
      itemTemplates.templateOf(request.itemId) != null && !itemTemplates.isBound(request.itemId)
    if (feePercent == null || !isPossible) {
      LOG.warn { "Master ${visit.masterId} sent an impossible blackboard posting: $request" }
      return OpError.REQUEST_REFUSED
    }
    if (postings.countByPosterMasterIdAndSettledFalse(visit.masterId) >= config.maxOpenPerMaster) {
      return OpError.BLACKBOARD_POSTING_LIMIT
    }

    val pay = request.amount * request.coinsEach
    val fee = BlackboardFees.feeFor(pay, feePercent)
    val paid = transactions.execute { status ->
      if (!inventoryService.consumeAll(visit.masterId, listOf(coinId to (pay + fee).toInt()))) {
        status.setRollbackOnly()
        return@execute false
      }
      val quest = quests.create(draftOf(visit, request))
      postings.save(BlackboardPosting(quest.id, visit.masterId, request.coinsEach, pay, fee, quest.deadline))
      true
    }
    if (paid != true) {
      return OpError.BLACKBOARD_CANNOT_AFFORD
    }

    world.takeFromLive(visit.masterEntityId, ReservedItem.stack(coinId, (pay + fee).toInt(), weight = 0))
    world.read { reserve.charge(-fee.toDouble(), CoinSource.QUEST_FEE) }

    return null
  }

  /** Starts the notice: the quest takes no new signers, and work under way is still paid until it runs out. */
  fun withdraw(masterId: Long, questId: Long): OpError? {
    return transactions.execute {
      val posting = postings.findLockedByQuestId(questId)
        ?.takeIf { it.posterMasterId == masterId && !it.settled && it.closesAt == null }
        ?: return@execute OpError.REQUEST_REFUSED
      posting.closesAt = clock.instant().plus(config.withdrawNotice)
      quests.close(questId)
      null
    }
  }

  @Scheduled(fixedDelay = 60 * 1000L)
  fun settleDue() {
    val now = clock.instant()
    transactions.executeWithoutResult {
      postings.findAllBySettledFalseAndClosesAtBefore(now).forEach { settle(it, QuestStatus.WITHDRAWN) }
      postings.findAllBySettledFalseAndDeadlineBefore(now).forEach { settle(it, QuestStatus.EXPIRED) }
    }
  }

  @EventListener
  fun onQuestsDeleted(event: QuestsDeletedEvent) {
    postings.deleteAllByQuestIdIn(event.questIds)
  }

  fun boardFor(visit: BoardVisit): BlackboardSMSG {
    val terms = BlackboardSMSG.Terms(
      feePercentByDays = config.feePercentByDays,
      openPostings = postings.countByPosterMasterIdAndSettledFalse(visit.masterId).toInt(),
      maxPostings = config.maxOpenPerMaster,
      maxAmount = config.maxAmount,
      maxCoinsEach = config.maxCoinsEach,
    )

    return BlackboardSMSG(visit.boardEntityId, visit.placeName, quests.boardFor(visit.masterId, QuestOrigin.BLACKBOARD), terms)
  }

  private fun settle(posting: BlackboardPosting, status: QuestStatus) {
    if (posting.settled) {
      return
    }
    quests.end(posting.questId, status)
    returnUnearnedPay(posting)
    posting.settled = true
  }

  private fun returnUnearnedPay(posting: BlackboardPosting) {
    if (posting.payLeft <= 0) {
      return
    }
    val coin = commodityItems.coinItemId()?.let { itemRepository.findByIdOrNull(it) } ?: return
    parcels.postInstant(posting.posterMasterId, SENDER_NAME) { it.addStackable(coin, posting.payLeft.toInt()) }
    posting.payLeft = 0
  }

  private fun draftOf(visit: BoardVisit, request: BlackboardRequest.Post): QuestDraft {
    return QuestDraft(
      origin = QuestOrigin.BLACKBOARD,
      titleKey = TITLE_KEY,
      textArgs = listOf(QuestTextArg.number("amount", request.amount.toLong()), QuestTextArg.item("item", request.itemId)),
      suggestedLevel = levelHint.suggestedLevelOf(request.itemId),
      deadline = clock.instant().plus(Duration.ofDays(request.days.toLong())),
      objectives = listOf(QuestObjective(ObjectiveKind.DELIVER_ITEM, ObjectiveScope.SHARED, request.amount, request.itemId)),
      rewards = listOf(QuestReward(QuestReward.Kind.COINS_PER_UNIT, request.coinsEach)),
      poster = QuestDraft.Poster(visit.masterId, visit.masterName),
    )
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val TITLE_KEY = "QUEST_DELIVER_TITLE"
    const val SENDER_NAME = "Blackboard"
  }
}
