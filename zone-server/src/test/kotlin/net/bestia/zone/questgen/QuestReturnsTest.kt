package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.quest.QuestService
import net.bestia.zone.questgen.ending.QuestReturns
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.treasure.Relics
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuestReturnsTest {

  private val stored = StoredQuestPlan(lostRelicPlan(BLADE, WorldTarget.townsfolk(1), home = HOME)).apply {
    state = StoredQuestPlan.State.RUNNING
    questId = QUEST
  }
  private val plans = mockk<StoredQuestPlanRepository> { every { findLocked(PLAN) } returns stored }
  private val quests = mockk<QuestService> { every { advancePersonal(AYLA.masterId, QUEST, BRING_STEP, 1) } returns 1 }
  private val inventory = mockk<InventoryService> {
    every { heldInstance(AYLA.masterId, any()) } returns mockk()
    every { destroyInstance(AYLA.masterId, any()) } returns true
  }
  private val relics = mockk<Relics>(relaxed = true) {
    every { artifactOf(any()) } returns null
    every { artifactOf(COPY) } returns BLADE
  }
  private val chronicle = mockk<LivingChronicle>(relaxed = true)

  private val returns = QuestReturns(plans, quests, inventory, relics, chronicle)

  @Test
  fun `the artifact leaves the bag, the step counts and the history says where it rests`() {
    assertNull(returns.handOver(AYLA, PLAN, COPY))

    verify { quests.advancePersonal(AYLA.masterId, QUEST, BRING_STEP, 1) }
    verify { inventory.destroyInstance(AYLA.masterId, COPY) }
    val actors = listOf(Actor(ActorType.ARTIFACT, BLADE), Actor(ActorType.SETTLEMENT, HOME))
    verify { chronicle.record(EventKind.ARTIFACT_ENSHRINED, actors, null, AYLA) }
    verify { relics.forget(listOf(COPY)) }
  }

  @Test
  fun `another relic is not taken for it`() {
    assertEquals(OpError.REQUEST_REFUSED, returns.handOver(AYLA, PLAN, relicCopy = 78))

    verify(exactly = 0) { quests.advancePersonal(any(), any(), any(), any()) }
    verify(exactly = 0) { inventory.destroyInstance(any(), any()) }
  }

  @Test
  fun `a master who did not choose to return it keeps it`() {
    every { quests.advancePersonal(AYLA.masterId, QUEST, BRING_STEP, 1) } returns 0

    assertEquals(OpError.REQUEST_REFUSED, returns.handOver(AYLA, PLAN, COPY))

    verify(exactly = 0) { inventory.destroyInstance(any(), any()) }
  }

  @Test
  fun `a quest that is over takes nothing`() {
    stored.state = StoredQuestPlan.State.ENDED

    assertEquals(OpError.QUEST_CLOSED, returns.handOver(AYLA, PLAN, COPY))
  }

  private companion object {
    const val PLAN = 4L
    const val QUEST = 40L
    const val BLADE = 3
    const val HOME = 6
    const val COPY = 77L

    /** The fixture's bring step is its fourth objective. */
    const val BRING_STEP = 3
    val AYLA = LivingChronicle.Doer(9, "Ayla")
  }
}
