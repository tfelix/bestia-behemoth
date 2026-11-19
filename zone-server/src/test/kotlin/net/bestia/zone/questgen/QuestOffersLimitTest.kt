package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.quest.QuestService
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuestOffersLimitTest {

  private val carried = StoredQuestPlan(carryNewsPlan(from = 4, to = 6, rumourId = 12)).apply {
    state = StoredQuestPlan.State.RUNNING
    questId = QUEST
  }
  private val plans = mockk<StoredQuestPlanRepository> { every { findLocked(PLAN) } returns carried }
  private val quests = mockk<QuestService>(relaxed = true) { every { accept(any(), QUEST) } returns null }

  private val offers = QuestOffers(plans, quests, mockk(relaxed = true), mockk(relaxed = true), QuestgenConfig(newsMaxCarriers = 3))

  @Test
  fun `news is carried by as many masters as the limit says, and no more`() {
    every { quests.holdersOf(QUEST) } returns setOf(1, 2)
    assertNull(offers.accept(MASTER, PLAN))

    every { quests.holdersOf(QUEST) } returns setOf(1, 2, 3)
    assertEquals(OpError.QUEST_CLOSED, offers.accept(MASTER, PLAN))

    verify(exactly = 1) { quests.accept(MASTER, QUEST) }
  }

  private companion object {
    const val PLAN = 30L
    const val QUEST = 31L
    const val MASTER = 9L
  }
}
