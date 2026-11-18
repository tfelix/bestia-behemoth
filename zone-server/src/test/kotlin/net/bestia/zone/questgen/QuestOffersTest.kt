package net.bestia.zone.questgen

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.fact.FactService
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.persistence.QuestLogEntryRepository
import net.bestia.zone.quest.persistence.QuestRepository
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("no-socket", "test")
class QuestOffersTest {

  @Autowired
  private lateinit var offers: QuestOffers

  @Autowired
  private lateinit var plans: StoredQuestPlanRepository

  @Autowired
  private lateinit var questService: QuestService

  @Autowired
  private lateinit var quests: QuestRepository

  @Autowired
  private lateinit var entries: QuestLogEntryRepository

  @Autowired
  private lateinit var facts: FactService

  private var planId = 0L

  @AfterEach
  fun forget() {
    val questId = plans.findByIdOrNull(planId)?.questId
    plans.deleteById(planId)
    if (questId != null) {
      entries.deleteAll(entries.findAllByQuestId(questId))
      quests.deleteById(questId)
    }
    listOf(ALICE, BOB).forEach(facts::deleteAllOf)
    offers.refresh()
  }

  @Test
  fun `the first master to accept makes the quest, the next one joins the race for it`() {
    planId = plans.save(StoredQuestPlan(lostRelicPlan(artifact = 9_001, GIVER)).apply { state = StoredQuestPlan.State.OFFERED }).id
    offers.refresh()
    assertEquals(planId, assertNotNull(offers.at(GIVER)).planId)

    assertNull(offers.accept(ALICE, planId))
    assertNull(offers.accept(BOB, planId))

    val stored = plans.findByIdOrNull(planId)!!
    assertEquals(StoredQuestPlan.State.RUNNING, stored.state)
    val alice = questService.logOf(ALICE).single()
    assertEquals(stored.questId, alice.questId)
    assertEquals(stored.questId, questService.logOf(BOB).single().questId)
    assertEquals(QuestOrigin.WORLD, alice.origin)
  }

  @Test
  fun `accepting from the giver is hearing them out`() {
    planId = plans.save(StoredQuestPlan(lostRelicPlan(artifact = 9_001, GIVER)).apply { state = StoredQuestPlan.State.OFFERED }).id

    offers.accept(ALICE, planId)

    val view = questService.logOf(ALICE).single()
    assertEquals(1, view.stage, "the first step is the giver's own tale")
    assertTrue(FactRef.event(30) in facts.factsOf(ALICE))
    assertEquals(WorldTarget.cache(55), questService.trackedFor(ALICE).deeds.single().target)
  }

  @Test
  fun `a plan that is not offered cannot be accepted`() {
    planId = plans.save(StoredQuestPlan(lostRelicPlan(artifact = 9_001, GIVER))).id

    assertEquals(OpError.QUEST_CLOSED, offers.accept(ALICE, planId))
    offers.refresh()
    assertNull(offers.at(GIVER))
  }

  private companion object {
    // Far above any master the scenario fixture creates.
    const val ALICE = 9_200_001L
    const val BOB = 9_200_002L
    val GIVER = WorldTarget.townsfolk(77_000_001)
  }
}
