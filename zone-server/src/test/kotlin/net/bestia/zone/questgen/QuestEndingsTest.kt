package net.bestia.zone.questgen

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.EventKind
import net.bestia.zone.chronicle.LivingChronicle
import net.bestia.zone.chronicle.persistence.WorldFactRepository
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactService
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.persistence.QuestLogEntryRepository
import net.bestia.zone.quest.persistence.QuestRepository
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlan.State
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A race for one blade, from the dig to the end of the quest. */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class QuestEndingsTest {

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

  @Autowired
  private lateinit var chronicle: LivingChronicle

  @Autowired
  private lateinit var worldFacts: WorldFactRepository

  private var planId = 0L
  private var relicPlanId = 0L

  @BeforeEach
  fun offerThePlan() {
    planId = plans.save(StoredQuestPlan(lostRelicPlan(BLADE, GIVER)).apply { state = State.OFFERED }).id
    relicPlanId = planId
    offers.refresh()
  }

  @AfterEach
  fun forget() {
    val questId = plans.findByIdOrNull(planId)?.questId
    plans.deleteAllById(setOf(planId, relicPlanId))
    if (questId != null) {
      entries.deleteAll(entries.findAllByQuestId(questId))
      quests.deleteById(questId)
    }
    worldFacts.deleteAll()
    listOf(ALICE, BOB).forEach(facts::deleteAllOf)
    offers.refresh()
  }

  @Test
  fun `the one who digs up the blade wins, the other is told who was faster`() {
    offers.accept(ALICE, planId)
    offers.accept(BOB, planId)
    val questId = plan().questId!!

    blade_is_taken_by(ALICE, "Alice")

    val lost = questService.viewFor(BOB, questId)!!
    assertEquals(QuestStatus.SUPERSEDED to "Alice", lost.status to lost.wonBy)
    assertEquals(QuestStatus.OPEN, questService.viewFor(ALICE, questId)!!.status)
    assertEquals(State.RUNNING, plan().state, "the winner's dig is still to be counted")

    questService.recordPersonal(ALICE, questId, objectiveIndex = 1, count = 1)

    assertEquals(QuestStatus.COMPLETED, questService.viewFor(ALICE, questId)!!.status)
    assertEquals(QuestStatus.SUPERSEDED, questService.viewFor(BOB, questId)!!.status)
    assertEquals(State.ENDED, plan().state)
    assertNull(offers.at(GIVER), "a finished quest is no longer offered")
  }

  @Test
  fun `a blade dug up by somebody outside the quest ends it for all`() {
    offers.accept(ALICE, planId)

    blade_is_taken_by(BOB, "Bob")

    val lost = questService.viewFor(ALICE, plan().questId!!)!!
    assertEquals(QuestStatus.SUPERSEDED to "Bob", lost.status to lost.wonBy)
    assertEquals(State.ENDED, plan().state)
  }

  @Test
  fun `a blade found before anybody took the quest withdraws the offer`() {
    blade_is_taken_by(BOB, "Bob")

    assertEquals(State.ENDED, plan().state)
    assertNull(offers.at(GIVER))
  }

  @Test
  fun `news is no race, and the quest closes a day and a half after the first master told it`() {
    planId = plans.save(StoredQuestPlan(carryNewsPlan(from = 4, to = 6, rumourId = 12, giver = NEWS_GIVER)).apply { state = State.OFFERED }).id
    offers.refresh()
    offers.accept(ALICE, planId)
    offers.accept(BOB, planId)
    val questId = plan().questId!!
    val before = Instant.now()

    questService.recordPersonal(ALICE, questId, objectiveIndex = 1, count = 1)

    assertEquals(QuestStatus.COMPLETED, questService.viewFor(ALICE, questId)!!.status)
    val stillOnTheWay = questService.viewFor(BOB, questId)!!
    assertEquals(QuestStatus.OPEN, stillOnTheWay.status)
    assertTrue(Duration.between(before, stillOnTheWay.deadline) <= Duration.ofHours(37), "was 30 days: ${stillOnTheWay.deadline}")
    assertEquals(State.RUNNING, plan().state)

    questService.recordPersonal(BOB, questId, objectiveIndex = 1, count = 1)

    assertEquals(QuestStatus.COMPLETED, questService.viewFor(BOB, questId)!!.status)

    questService.end(questId, QuestStatus.EXPIRED)

    assertEquals(QuestStatus.COMPLETED, questService.viewFor(ALICE, questId)!!.status, "the deadline takes nothing from those who made it")
  }

  private fun blade_is_taken_by(masterId: Long, name: String) {
    chronicle.record(EventKind.ARTIFACT_TAKEN, listOf(Actor(ActorType.ARTIFACT, BLADE)), by = LivingChronicle.Doer(masterId, name))
  }

  private fun plan(): StoredQuestPlan {
    return plans.findByIdOrNull(planId)!!
  }

  private companion object {
    /** A real artifact of the test world that is not lost, so no plan of the world itself is about it. */
    const val BLADE = 0

    // Far above any master the scenario fixture creates.
    const val ALICE = 9_300_001L
    const val BOB = 9_300_002L
    val GIVER = WorldTarget.townsfolk(77_000_002)
    val NEWS_GIVER = WorldTarget.townsfolk(77_000_003)
  }
}
