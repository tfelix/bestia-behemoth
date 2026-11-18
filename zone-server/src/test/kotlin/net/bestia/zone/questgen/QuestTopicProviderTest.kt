package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.pop.Kinship
import net.bestia.worldgen.pop.Member
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.offer.QuestTopicProvider
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.townsfolk.conversation.Asker
import net.bestia.zone.townsfolk.conversation.OptionKind
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.townsfolk.domain.Occupation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuestTopicProviderTest {

  private val offers = mockk<QuestOffers> {
    every { at(any()) } returns null
    every { at(GIVER) } returns QuestOffers.Offer(PLAN_ID, lostRelicPlan(artifact = 3, GIVER))
  }
  private val sessions = mockk<ConnectionInfoService> { every { activeMasters() } returns mapOf(ACCOUNT to MASTER) }

  // Runs each job at once, so the test sees what it did.
  private val jobs = mockk<AsyncJobExecutor> {
    val job = slot<() -> Unit>()
    every { submit(any(), capture(job)) } answers { job.captured() }
  }
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val provider = QuestTopicProvider(offers, sessions, jobs, out)

  @Test
  fun `only the townsperson a quest starts with speaks of it`() {
    assertEquals(1, provider.rootOptions(speaker(GIVER_IDENTITY)).size)
    assertTrue(provider.rootOptions(speaker(identity = 5)).isEmpty())
  }

  @Test
  fun `the giver tells of the quest in its own words and lets the player take it`() {
    val giver = speaker(GIVER_IDENTITY)
    val told = assertNotNull(provider.nodeFor(ASKER, giver, provider.rootOptions(giver).single().topicId))

    assertEquals("QUESTGEN_LOST_RELIC_OFFER", told.speech.key)
    val accept = told.options.single()
    assertEquals(OptionKind.ACTION, accept.kind)

    every { offers.accept(MASTER, PLAN_ID) } returns null
    assertNotNull(provider.nodeFor(ASKER, giver, accept.topicId))

    verify { offers.accept(MASTER, PLAN_ID) }
    verify(exactly = 0) { out.sendToPlayer(any(), any<OperationErrorSMSG>()) }
  }

  @Test
  fun `a refused accept is told to the player`() {
    val giver = speaker(GIVER_IDENTITY)
    val accept = provider.nodeFor(ASKER, giver, provider.rootOptions(giver).single().topicId)!!.options.single()
    every { offers.accept(MASTER, PLAN_ID) } returns OpError.QUEST_LOG_FULL

    provider.nodeFor(ASKER, giver, accept.topicId)

    verify { out.sendToPlayer(ACCOUNT, match<OperationErrorSMSG> { it.code == OpError.QUEST_LOG_FULL }) }
  }

  private fun speaker(identity: Long): Speaker {
    return Speaker(
      entityId = 1L,
      identity = identity,
      settlement = 3,
      household = 4,
      name = "Otbert",
      occupation = Occupation("farmer", "farmer", null, HourWindow(6, 18), HourWindow(22, 6)),
      business = null,
      member = Member(40, Kinship.HEAD),
      seed = 7L,
    )
  }

  private companion object {
    const val ACCOUNT = 8L
    const val MASTER = 80L
    const val PLAN_ID = 12L
    const val GIVER_IDENTITY = 42L
    val GIVER = WorldTarget.townsfolk(GIVER_IDENTITY)
    val ASKER = Asker(ACCOUNT, entityId = 2L)
  }
}
