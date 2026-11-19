package net.bestia.zone.questgen

import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.questgen.ending.QuestReturns
import net.bestia.zone.treasure.Relics
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
import net.bestia.zone.text.WorldTexts
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
    every { broughtTo(any()) } returns null
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

  private val world = testWorld()
  private val bag = Inventory(mutableListOf(Inventory.Item(itemId = RELIC_ITEM_ID, amount = 1, uniqueId = RELIC_COPY, stackable = false)))
  private val masterEntity = world.createEntity { id ->
    add(id, Master(masterId = MASTER, name = "Ayla"))
    add(id, bag)
  }

  // Reads and posts run on the test world at once.
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) }
    every { post(any()) } answers { firstArg<World.() -> Unit>().invoke(world) }
  }
  private val masters = mockk<MasterEntityResolver> { every { selectedMasterEntityOf(ACCOUNT) } returns masterEntity }
  private val relics = mockk<Relics> {
    every { artifactOf(any()) } returns null
    every { artifactOf(RELIC_COPY) } returns BLADE
  }
  // No text is written in these tests, so every line stays its template.
  private val texts = WorldTexts(mockk())
  private val returns = mockk<QuestReturns> { every { handOver(any(), any(), any()) } returns null }

  private val provider = QuestTopicProvider(offers, sessions, jobs, out, returns, relics, view, texts, masters)

  @Test
  fun `in the artifact's home town a head of household takes it back from the master who carries it`() {
    every { offers.broughtTo(HOME) } returns QuestOffers.Offer(PLAN_ID, lostRelicPlan(BLADE, GIVER, home = HOME))
    val elder = speaker(identity = 5, settlement = HOME)
    assertTrue(provider.rootOptions(speaker(identity = 6, settlement = HOME, kinship = Kinship.CHILD)).isEmpty())

    val told = assertNotNull(provider.nodeFor(ASKER, elder, provider.rootOptions(elder).single().topicId))

    assertEquals("QUESTGEN_HOME_AGAIN", told.speech.key)
    verify { returns.handOver(LivingChronicle.Doer(MASTER, "Ayla"), PLAN_ID, RELIC_COPY) }
    assertTrue(bag.getItems().isEmpty(), "the live bag follows the database")
  }

  @Test
  fun `a master without the artifact is told the town still waits`() {
    every { offers.broughtTo(HOME) } returns QuestOffers.Offer(PLAN_ID, lostRelicPlan(artifact = 99, GIVER, home = HOME))
    val elder = speaker(identity = 5, settlement = HOME)

    val told = assertNotNull(provider.nodeFor(ASKER, elder, provider.rootOptions(elder).single().topicId))

    assertEquals("QUESTGEN_STILL_WAITING", told.speech.key)
    verify(exactly = 0) { returns.handOver(any(), any(), any()) }
  }

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

  private fun speaker(identity: Long, settlement: Int = 3, kinship: Kinship = Kinship.HEAD): Speaker {
    return Speaker(
      entityId = 1L,
      identity = identity,
      settlement = settlement,
      household = 4,
      name = "Otbert",
      occupation = Occupation("farmer", "farmer", null, HourWindow(6, 18), HourWindow(22, 6)),
      business = null,
      member = Member(40, kinship),
      seed = 7L,
    )
  }

  private companion object {
    const val ACCOUNT = 8L
    const val MASTER = 80L
    const val PLAN_ID = 12L
    const val HOME = 6
    const val BLADE = 3
    const val RELIC_COPY = 77L
    const val GIVER_IDENTITY = 42L
    val GIVER = WorldTarget.townsfolk(GIVER_IDENTITY)
    val ASKER = Asker(ACCOUNT, entityId = 2L)
  }
}
