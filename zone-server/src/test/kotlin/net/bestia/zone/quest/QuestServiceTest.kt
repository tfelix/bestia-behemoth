package net.bestia.zone.quest

import net.bestia.zone.entity.observe.DeedFact
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.quest.persistence.QuestLogEntryRepository
import net.bestia.zone.quest.persistence.QuestRepository
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextRef
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import kotlin.test.assertFailsWith
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("no-socket", "test")
@RecordApplicationEvents
class QuestServiceTest {

  @Autowired
  private lateinit var service: QuestService

  @Autowired
  private lateinit var quests: QuestRepository

  @Autowired
  private lateinit var entries: QuestLogEntryRepository

  @Autowired
  private lateinit var events: ApplicationEventPublisher

  @Autowired
  private lateinit var config: QuestConfig

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  @Autowired
  private lateinit var live: LiveQuests

  @Autowired
  private lateinit var published: ApplicationEvents

  // Far above any master the scenario fixture creates: this slice keeps master ids without a foreign key.
  private val poster = 9_100_001L
  private val alice = 9_100_002L
  private val bob = 9_100_003L

  @AfterEach
  fun deleteQuests() {
    entries.deleteAll()
    quests.deleteAll()
  }

  @Test
  fun `an accepted quest shows in the log, and the poster sees it as posted`() {
    val questId = postDeliveryOf(required = 10)

    assertNull(service.accept(alice, questId))

    val accepted = service.logOf(alice).single()
    assertEquals(QuestView.Role.ACCEPTED, accepted.role)
    assertEquals(IRON_ORE, accepted.objectives.single().itemId)
    assertEquals(TextArg.Item(IRON_ORE), accepted.title.args["item"])
    assertEquals(QuestView.Role.POSTED, service.logOf(poster).single().role)
    assertEquals(setOf(poster, alice), service.holdersOf(questId))
  }

  @Test
  fun `a board shows each master its own, its taken and the offered quests, but not ended ones`() {
    val taken = postDeliveryOf(required = 10)
    val offered = postDeliveryOf(required = 5)
    val ended = postDeliveryOf(required = 5)
    service.accept(alice, taken)
    service.end(ended, QuestStatus.EXPIRED)

    val roles = service.boardFor(alice, QuestOrigin.BLACKBOARD).associate { it.questId to it.role }

    assertEquals(mapOf(taken to QuestView.Role.ACCEPTED, offered to QuestView.Role.OFFERED), roles)
    assertTrue(service.boardFor(poster, QuestOrigin.BLACKBOARD).all { it.role == QuestView.Role.POSTED })
  }

  @Test
  fun `accepting is refused for the poster, twice, a closed quest and a full log`() {
    val questId = postDeliveryOf(required = 10)

    assertEquals(OpError.REQUEST_REFUSED, service.accept(poster, questId))
    assertNull(service.accept(alice, questId))
    assertEquals(OpError.REQUEST_REFUSED, service.accept(alice, questId))

    service.close(questId)
    assertEquals(OpError.QUEST_CLOSED, service.accept(bob, questId))

    repeat(config.logCapacity) { assertNull(service.accept(bob, postDeliveryOf(required = 1))) }
    assertEquals(OpError.QUEST_LOG_FULL, service.accept(bob, postDeliveryOf(required = 1)))
  }

  @Test
  fun `shared work counts per signer, stops at the target and completes the quest`() {
    val questId = postDeliveryOf(required = 10)
    service.accept(alice, questId)
    service.accept(bob, questId)

    assertEquals(4, service.advanceShared(questId, 0, 4, alice))
    assertEquals(6, service.advanceShared(questId, 0, 9, bob), "only what the quest still needs counts")
    assertEquals(0, service.advanceShared(questId, 0, 1, alice), "a completed quest takes no more work")

    val bobsView = service.viewFor(bob, questId)!!
    assertEquals(QuestStatus.COMPLETED, bobsView.status)
    assertEquals(10, bobsView.objectives.single().progress)
    assertEquals(6, bobsView.objectives.single().yours)
    assertEquals(4, service.viewFor(alice, questId)!!.objectives.single().yours)
  }

  @Test
  fun `work from a master who did not accept counts nothing`() {
    val questId = postDeliveryOf(required = 10)

    assertEquals(0, service.advanceShared(questId, 0, 3, alice))
  }

  @Test
  fun `a signer drops a quest, the poster clears it only once it ended`() {
    val questId = postDeliveryOf(required = 10)
    service.accept(alice, questId)

    assertTrue(service.abandon(alice, questId))
    assertTrue(service.logOf(alice).isEmpty())

    assertFalse(service.abandon(poster, questId), "a running quest is withdrawn at a board, not dropped")
    service.end(questId, QuestStatus.WITHDRAWN)
    assertTrue(service.abandon(poster, questId))
    assertTrue(service.logOf(poster).isEmpty())
  }

  @Test
  fun `quests that ended long ago are deleted with their entries`() {
    val ended = postDeliveryOf(required = 10)
    val running = postDeliveryOf(required = 10)
    service.accept(alice, ended)
    service.end(ended, QuestStatus.EXPIRED)

    val later = Clock.fixed(Instant.now().plus(config.endedKeptFor).plus(Duration.ofMinutes(1)), ZoneOffset.UTC)
    // Built by hand for its clock, so it has no transactional proxy of its own.
    TransactionTemplate(transactionManager).executeWithoutResult {
      QuestService(quests, entries, events, config, live, later).deleteLongEnded()
    }

    assertFalse(quests.existsById(ended))
    assertTrue(quests.existsById(running))
    assertTrue(service.logOf(alice).isEmpty())
  }

  @Test
  fun `an accepted kill quest is tracked with its count, and a recorded count is kept`() {
    val questId = service.create(killDraft(required = 5)).id
    service.accept(alice, questId)

    val tracked = service.trackedFor(alice).kills.single()
    assertEquals(WOLF, tracked.speciesId)
    assertEquals(QuestArea.InPlace("Ashford"), tracked.area)
    assertEquals(0, tracked.count)

    service.recordPersonal(alice, questId, 0, 3)
    service.recordPersonal(alice, questId, 0, 2)

    assertEquals(3, service.trackedFor(alice).kills.single().count, "a late, lower count changes nothing")
    assertEquals(3, service.viewFor(alice, questId)!!.objectives.single().progress)
    assertEquals("Ashford", service.viewFor(alice, questId)!!.objectives.single().placeName)
  }

  @Test
  fun `an ended kill quest is not tracked`() {
    val questId = service.create(killDraft(required = 5)).id
    service.accept(alice, questId)
    service.end(questId, QuestStatus.EXPIRED)

    assertTrue(service.trackedFor(alice).kills.isEmpty())
  }

  @Test
  fun `a master works through the stages one at a time and sees only the ones it reached`() {
    val questId = service.create(stagedKillDraft()).id
    service.accept(alice, questId)
    service.accept(bob, questId)

    assertEquals(listOf(WOLF), service.trackedFor(alice).kills.map { it.speciesId })
    assertEquals(1, service.viewFor(alice, questId)!!.objectives.size)

    service.recordPersonal(alice, questId, 0, 2)

    assertEquals(listOf(BEAR), service.trackedFor(alice).kills.map { it.speciesId })
    val view = service.viewFor(alice, questId)!!
    assertEquals(1, view.stage)
    assertEquals(listOf(0, 1), view.objectives.map { it.stage })
    assertEquals(0, service.viewFor(bob, questId)!!.stage, "each master moves on by its own counts")

    service.recordPersonal(alice, questId, 1, 1)

    assertTrue(service.trackedFor(alice).kills.isEmpty())
    assertEquals(
      listOf(QuestStageDoneEvent(questId, alice, 0, wasLast = false), QuestStageDoneEvent(questId, alice, 1, wasLast = true)),
      published.stream(QuestStageDoneEvent::class.java).toList(),
    )
  }

  @Test
  fun `a quest with stages takes personal objectives only, and its stages have no gap`() {
    val shared = QuestObjective(ObjectiveKind.DELIVER_ITEM, ObjectiveScope.SHARED, 5, IRON_ORE, stage = 1)
    val late = QuestObjective(ObjectiveKind.KILL, ObjectiveScope.PERSONAL, 1, speciesId = BEAR, stage = 2)

    assertFailsWith<IllegalArgumentException> { stagedKillDraft().let { it.copy(objectives = it.objectives.take(1) + shared) } }
    assertFailsWith<IllegalArgumentException> { stagedKillDraft().let { it.copy(objectives = it.objectives.take(1) + late) } }
  }

  @Test
  fun `an examine and a talk objective are tracked by their target, one stage at a time`() {
    val epitaph = WorldTarget.prop(77)
    val keeper = WorldTarget.townsfolk(4_000_012)
    val name = FactRef(FactRef.Subject.FIGURE, 7, FactRef.Aspect.NAME)
    val draft = killDraft(required = 1).copy(
      objectives = listOf(
        QuestObjective(ObjectiveKind.EXAMINE, ObjectiveScope.PERSONAL, 1, target = epitaph, text = TextRef("QUEST_STEP_READ")),
        QuestObjective(
          ObjectiveKind.TALK, ObjectiveScope.PERSONAL, 1, target = keeper, needsFact = name, stage = 1,
          text = TextRef("QUEST_STEP_TELL"),
        ),
      ),
    )
    val questId = service.create(draft).id
    service.accept(alice, questId)

    val read = service.trackedFor(alice).deeds.single()
    assertEquals(DeedFact.Deed.EXAMINED to epitaph, read.deed to read.target)

    service.recordPersonal(alice, questId, 0, 1)

    val tell = service.trackedFor(alice).deeds.single()
    assertEquals(DeedFact.Deed.TALKED_TO to keeper, tell.deed to tell.target)
    assertEquals(name, tell.needsFact)
    assertEquals(listOf("QUEST_STEP_READ", "QUEST_STEP_TELL"), service.viewFor(alice, questId)!!.objectives.map { it.text?.key })
  }

  @Test
  fun `an examine objective without a target or without a text is refused`() {
    val bare = QuestObjective(ObjectiveKind.EXAMINE, ObjectiveScope.PERSONAL, 1)

    for (objective in listOf(bare.copy(text = TextRef("QUEST_STEP_READ")), bare.copy(target = WorldTarget.prop(77)))) {
      assertFailsWith<IllegalArgumentException> { killDraft(required = 1).copy(objectives = listOf(objective)) }
    }
  }

  @Test
  fun `a pick is final, and what follows depends on it`() {
    val questId = service.create(artifactDraft()).id
    service.accept(alice, questId)
    service.accept(bob, questId)

    assertEquals(OpError.REQUEST_REFUSED, service.decide(alice, questId, "keep"), "the dig comes first")
    service.recordPersonal(alice, questId, 0, 1)
    service.recordPersonal(bob, questId, 0, 1)
    assertEquals(OpError.REQUEST_REFUSED, service.decide(alice, questId, "sell"))

    assertNull(service.decide(alice, questId, "keep"))
    assertEquals(OpError.REQUEST_REFUSED, service.decide(alice, questId, "return"))
    val kept = service.viewFor(alice, questId)!!
    assertEquals("keep", kept.objectives.last().chosen)
    assertEquals(listOf(ObjectiveKind.EXAMINE, ObjectiveKind.DECIDE), kept.objectives.map { it.kind }, "keeping ends the chain")
    assertTrue(service.trackedFor(alice).isEmpty)

    assertNull(service.decide(bob, questId, "return"))
    assertEquals(TEMPLE_KEEPER, service.trackedFor(bob).deeds.single().target)
    assertEquals(ObjectiveKind.TALK, service.viewFor(bob, questId)!!.objectives.last().kind)

    assertEquals(
      listOf(
        QuestStageDoneEvent(questId, alice, 1, wasLast = true, choice = "keep"),
        QuestStageDoneEvent(questId, bob, 1, wasLast = false, choice = "return"),
      ),
      published.stream(QuestStageDoneEvent::class.java).filter { it.stage == 1 }.toList(),
    )
  }

  @Test
  fun `goods a source takes count towards a bring objective of the stage the master works on`() {
    val bring = QuestObjective(
      ObjectiveKind.BRING, ObjectiveScope.PERSONAL, 3, itemId = IRON_ORE, target = TEMPLE_KEEPER, stage = 1,
      text = TextRef("QUEST_STEP_BRING"),
    )
    val questId = service.create(killDraft(required = 1).let { it.copy(objectives = it.objectives + bring) }).id
    service.accept(alice, questId)

    assertEquals(0, service.advancePersonal(alice, questId, 1, 2), "the kill comes first")
    service.recordPersonal(alice, questId, 0, 1)

    assertEquals(2, service.advancePersonal(alice, questId, 1, 2))
    assertEquals(1, service.advancePersonal(alice, questId, 1, 5), "never more than is still needed")
    assertEquals(0, service.advancePersonal(bob, questId, 1, 1), "bob did not accept")
    assertEquals(2, service.viewFor(alice, questId)!!.stage)
    assertFailsWith<IllegalArgumentException> { killDraft(required = 1).copy(objectives = listOf(bring.copy(itemId = null, stage = 0))) }
  }

  @Test
  fun `a decide objective needs choices, and a branch needs a choice offered before it`() {
    val draft = artifactDraft()
    val decide = draft.objectives[1]
    val branch = draft.objectives[2]

    assertFailsWith<IllegalArgumentException> { draft.copy(objectives = listOf(draft.objectives[0], decide.copy(choices = decide.choices.take(1)))) }
    assertFailsWith<IllegalArgumentException> { draft.copy(objectives = draft.objectives.take(2) + branch.copy(afterChoice = "sell")) }
  }

  /** Dig up the blade, then keep it, or bring word to the keeper of its temple. */
  private fun artifactDraft(): QuestDraft {
    val choices = listOf(
      QuestObjective.Choice("keep", TextRef("QUEST_CHOICE_KEEP")),
      QuestObjective.Choice("return", TextRef("QUEST_CHOICE_RETURN")),
    )

    return killDraft(required = 1).copy(
      objectives = listOf(
        QuestObjective(ObjectiveKind.EXAMINE, ObjectiveScope.PERSONAL, 1, target = WorldTarget.prop(77), text = TextRef("QUEST_STEP_DIG")),
        QuestObjective(ObjectiveKind.DECIDE, ObjectiveScope.PERSONAL, 1, stage = 1, text = TextRef("QUEST_STEP_DECIDE"), choices = choices),
        QuestObjective(
          ObjectiveKind.TALK, ObjectiveScope.PERSONAL, 1, stage = 2, target = TEMPLE_KEEPER, afterChoice = "return",
          text = TextRef("QUEST_STEP_RETURN"),
        ),
      ),
    )
  }

  private fun stagedKillDraft(): QuestDraft {
    return killDraft(required = 2).let { draft ->
      draft.copy(
        objectives = draft.objectives +
          QuestObjective(ObjectiveKind.KILL, ObjectiveScope.PERSONAL, 1, speciesId = BEAR, stage = 1),
      )
    }
  }

  private fun killDraft(required: Int): QuestDraft {
    val objective = QuestObjective(
      ObjectiveKind.KILL, ObjectiveScope.PERSONAL, required, speciesId = WOLF, area = QuestArea.InPlace("Ashford"),
    )

    return QuestDraft(
      origin = QuestOrigin.BLACKBOARD,
      title = TextRef("QUEST_KILL_TITLE", mapOf("amount" to TextArg.Number(required.toLong()))),
      suggestedLevel = 10,
      deadline = Instant.now().plus(Duration.ofDays(1)),
      objectives = listOf(objective),
      rewards = emptyList(),
    )
  }

  private fun postDeliveryOf(required: Int): Long {
    val draft = QuestDraft(
      origin = QuestOrigin.BLACKBOARD,
      title = TextRef(
        "QUEST_DELIVER_TITLE",
        mapOf("amount" to TextArg.Number(required.toLong()), "item" to TextArg.Item(IRON_ORE)),
      ),
      suggestedLevel = 20,
      deadline = Instant.now().plus(Duration.ofDays(1)),
      objectives = listOf(QuestObjective(ObjectiveKind.DELIVER_ITEM, ObjectiveScope.SHARED, required, IRON_ORE)),
      rewards = listOf(QuestReward(QuestReward.Kind.COINS_PER_UNIT, 12)),
      poster = QuestDraft.Poster(poster, "Poster"),
    )

    return service.create(draft).id
  }

  private companion object {
    const val IRON_ORE = 8L
    const val WOLF = 3L
    val TEMPLE_KEEPER = WorldTarget.townsfolk(4_000_012)
    const val BEAR = 4L
  }
}
