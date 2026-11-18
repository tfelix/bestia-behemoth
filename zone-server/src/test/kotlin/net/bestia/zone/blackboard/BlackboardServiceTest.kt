package net.bestia.zone.blackboard

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.blackboard.persistence.BlackboardPosting
import net.bestia.zone.blackboard.persistence.BlackboardPostingRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.quest.QuestDraft
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.persistence.Quest
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestView
import net.bestia.zone.text.TextRef
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlackboardServiceTest {

  private val now = Instant.parse("2026-11-17T12:00:00Z")
  private val config = BlackboardConfig()

  private val rows = ArrayList<BlackboardPosting>()
  private val postings = mockk<BlackboardPostingRepository>(relaxed = true) {
    val saved = slot<BlackboardPosting>()
    every { save(capture(saved)) } answers { saved.captured.also { rows += it } }
    every { findLockedByQuestId(any()) } answers { rows.firstOrNull { it.questId == firstArg() } }
    every { countByPosterMasterIdAndSettledFalse(any()) } answers { rows.count { it.posterMasterId == firstArg() && !it.settled }.toLong() }
    every { findAllBySettledFalseAndClosesAtBefore(any()) } answers {
      rows.filter { !it.settled && it.closesAt?.isBefore(firstArg()) == true }
    }
    every { findAllBySettledFalseAndDeadlineBefore(any()) } answers { rows.filter { !it.settled && it.deadline.isBefore(firstArg()) } }
  }

  private val drafts = ArrayList<QuestDraft>()
  private val quests = mockk<QuestService>(relaxed = true) {
    val draft = slot<QuestDraft>()
    every { create(capture(draft)) } answers { questOf(draft.captured).also { drafts += draft.captured } }
  }

  private val inventory = mockk<InventoryService>(relaxed = true) {
    every { consumeAll(any(), any(), any()) } returns true
    every { moveToEscrow(any(), IRON_ORE, any(), any(), any(), any()) } answers {
      ReservedItem.stack(IRON_ORE, arg(3), weight = 0)
    }
  }
  private val parcels = mockk<ParcelService>(relaxed = true) {
    every { postInstant(any(), any(), any()) } answers { thirdArg<(ItemContainer) -> Unit>().invoke(mockk(relaxed = true)) }
  }
  private val reserve = mockk<WorldReserve>(relaxed = true)
  private val liveWorld = testWorld()
  private val deliverer = liveWorld.createEntity { id ->
    add(id, Inventory(mutableListOf(Inventory.Item(IRON_ORE, amount = 6), Inventory.Item(IRON_ORE, amount = 3))))
  }
  private val world = mockk<WorldView>(relaxed = true).also { view ->
    every { view.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(liveWorld) }
    // The poster's master is not in this world, so its live bag has nothing to mirror.
    every { view.modify<Any?>(any(), any()) } returns false
  }
  private val transactions = mockk<TransactionTemplate>().also { template ->
    every { template.execute<Any?>(any()) } answers {
      firstArg<TransactionCallback<Any?>>().doInTransaction(mockk<TransactionStatus>(relaxed = true))
    }
    every { template.executeWithoutResult(any()) } answers {
      firstArg<Consumer<TransactionStatus>>().accept(mockk(relaxed = true))
    }
  }

  private fun serviceAt(at: Instant): BlackboardService {
    return BlackboardService(
      quests, postings, inventory,
      mockk<CommodityItems> { every { coinItemId() } returns COIN },
      mockk<ItemTemplateRegistry> {
        every { templateOf(any()) } returns mockk()
        every { isBound(any()) } returns false
      },
      mockk<ItemRepository> { every { findById(COIN) } returns Optional.of(mockk<Item>()) }, parcels, reserve,
      mockk<QuestLevelHint> { every { suggestedLevelOf(IRON_ORE) } returns 18 },
      world, mockk(relaxed = true), transactions, config, Clock.fixed(at, ZoneOffset.UTC),
    )
  }

  private val service = serviceAt(now)

  @Test
  fun `posting takes the pay and the fee at once, and the fee goes to the reserve`() {
    assertNull(service.post(visit(), post(amount = 40, coinsEach = 12, days = 3)))

    verify { inventory.consumeAll(POSTER, listOf(COIN to 504), any()) }
    verify { reserve.charge(-24.0, CoinSource.QUEST_FEE) }
    assertEquals(480, rows.single().payLeft)
    assertEquals(18, drafts.single().suggestedLevel)
    assertEquals(now.plus(Duration.ofDays(3)), drafts.single().deadline)
  }

  @Test
  fun `a poster short of coins posts nothing`() {
    every { inventory.consumeAll(any(), any(), any()) } returns false

    assertEquals(OpError.BLACKBOARD_CANNOT_AFFORD, service.post(visit(), post()))
    assertTrue(rows.isEmpty())
  }

  @Test
  fun `a run the board does not offer is refused`() {
    assertEquals(OpError.REQUEST_REFUSED, service.post(visit(), post(days = 2)))
  }

  @Test
  fun `a poster may keep only so many quests open`() {
    repeat(config.maxOpenPerMaster) { assertNull(service.post(visit(), post())) }

    assertEquals(OpError.BLACKBOARD_POSTING_LIMIT, service.post(visit(), post()))
  }

  @Test
  fun `a withdrawn quest keeps taking hand-ins for the notice, then the unearned pay goes home`() {
    service.post(visit(), post(amount = 10, coinsEach = 5))
    val questId = rows.single().questId

    assertEquals(OpError.REQUEST_REFUSED, service.withdraw(OTHER, questId), "only the poster withdraws")
    assertNull(service.withdraw(POSTER, questId))
    verify { quests.close(questId) }

    service.settleDue()
    verify(exactly = 0) { quests.end(any(), any()) }

    serviceAt(now.plus(config.withdrawNotice).plusSeconds(1)).settleDue()
    verify { quests.end(questId, QuestStatus.WITHDRAWN) }
    verify { parcels.postInstant(POSTER, any(), any()) }
    assertEquals(0, rows.single().payLeft)
  }

  @Test
  fun `a quest past its deadline expires once and its pay goes home`() {
    service.post(visit(), post(days = 1))
    val later = serviceAt(now.plus(Duration.ofDays(1)).plusSeconds(1))

    later.settleDue()
    later.settleDue()

    verify(exactly = 1) { quests.end(rows.single().questId, QuestStatus.EXPIRED) }
    verify(exactly = 1) { parcels.postInstant(POSTER, any(), any()) }
  }

  @Test
  fun `a hand-in mails the goods to the poster and pays the deliverer from the posting`() {
    service.post(visit(), post(amount = 10, coinsEach = 5))
    val questId = rows.single().questId
    signedView(questId, required = 10, progress = 0)
    every { quests.advanceShared(questId, 0, 8, DELIVERER) } returns 8

    assertNull(service.handIn(delivererVisit(), BlackboardRequest.HandIn(questId, amount = 8)))

    verify { parcels.postInstant(POSTER, any(), any()) }
    verify { inventory.moveToEscrow(DELIVERER, IRON_ORE, any(), 6, any(), any()) }
    verify { inventory.moveToEscrow(DELIVERER, IRON_ORE, any(), 2, any(), any()) }
    verify { inventory.grantToMaster(DELIVERER, COIN, 40) }
    assertEquals(10L, rows.single().payLeft)
  }

  @Test
  fun `the last hand-in completes the quest and settles the posting`() {
    service.post(visit(), post(amount = 4, coinsEach = 5))
    val questId = rows.single().questId
    signedView(questId, required = 4, progress = 0, afterwards = QuestStatus.COMPLETED)
    every { quests.advanceShared(questId, 0, 4, DELIVERER) } returns 4

    assertNull(service.handIn(delivererVisit(), BlackboardRequest.HandIn(questId, amount = 9)))

    assertTrue(rows.single().settled)
    assertEquals(0L, rows.single().payLeft)
  }

  @Test
  fun `only a signer hands in, and only what it carries`() {
    service.post(visit(), post())
    val questId = rows.single().questId

    every { quests.viewFor(DELIVERER, questId) } returns null
    assertEquals(OpError.REQUEST_REFUSED, service.handIn(delivererVisit(), BlackboardRequest.HandIn(questId, 1)))

    signedView(questId, required = 10, progress = 0, itemId = 99L)
    assertEquals(OpError.BLACKBOARD_NOTHING_TO_HAND_IN, service.handIn(delivererVisit(), BlackboardRequest.HandIn(questId, 1)))
  }

  private fun delivererVisit(): BoardVisit {
    return BoardVisit(accountId = 2, masterId = DELIVERER, masterEntityId = deliverer, masterName = "Mira", boardEntityId = 200, placeName = "Ashford")
  }

  private fun signedView(
    questId: Long,
    required: Int,
    progress: Int,
    afterwards: QuestStatus = QuestStatus.OPEN,
    itemId: Long = IRON_ORE,
  ) {
    val objective = QuestView.Objective(ObjectiveKind.DELIVER_ITEM, ObjectiveScope.SHARED, itemId, null, null, required, progress, 0)
    fun viewIn(status: QuestStatus) = QuestView(
      questId, QuestOrigin.BLACKBOARD, status, QuestView.Role.ACCEPTED, TextRef("QUEST_DELIVER_TITLE"), 18, now,
      "Kaelin", listOf(objective), emptyList(),
    )
    every { quests.viewFor(DELIVERER, questId) } returnsMany listOf(viewIn(QuestStatus.OPEN), viewIn(afterwards))
  }

  private fun visit(): BoardVisit {
    return BoardVisit(accountId = 1, masterId = POSTER, masterEntityId = 100, masterName = "Kaelin", boardEntityId = 200, placeName = "Ashford")
  }

  private fun post(amount: Int = 40, coinsEach: Long = 12, days: Int = 3): BlackboardRequest.Post {
    return BlackboardRequest.Post(IRON_ORE, amount, coinsEach, days)
  }

  private var nextQuestId = 1L

  private fun questOf(draft: QuestDraft): Quest {
    val quest = mockk<Quest>()
    every { quest.id } returns nextQuestId++
    every { quest.deadline } returns draft.deadline

    return quest
  }

  private companion object {
    const val COIN = 1L
    const val IRON_ORE = 8L
    const val POSTER = 10L
    const val OTHER = 11L
    const val DELIVERER = 12L
  }
}
