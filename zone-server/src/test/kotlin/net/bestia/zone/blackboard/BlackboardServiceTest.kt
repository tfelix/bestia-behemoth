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

  private val inventory = mockk<InventoryService> { every { consumeAll(any(), any(), any()) } returns true }
  private val parcels = mockk<ParcelService>(relaxed = true)
  private val reserve = mockk<WorldReserve>(relaxed = true)
  private val world = mockk<WorldView>(relaxed = true).also { view ->
    every { view.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(mockk()) }
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
  }
}
