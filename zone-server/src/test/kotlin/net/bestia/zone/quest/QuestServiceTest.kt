package net.bestia.zone.quest

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.quest.persistence.QuestLogEntryRepository
import net.bestia.zone.quest.persistence.QuestRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
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
    assertEquals(listOf(IRON_ORE.toString()), accepted.textArgs.filter { it.kind == QuestTextArg.Kind.ITEM }.map { it.value })
    assertEquals(QuestView.Role.POSTED, service.logOf(poster).single().role)
    assertEquals(setOf(poster, alice), service.holdersOf(questId))
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
      QuestService(quests, entries, events, config, later).deleteLongEnded()
    }

    assertFalse(quests.existsById(ended))
    assertTrue(quests.existsById(running))
    assertTrue(service.logOf(alice).isEmpty())
  }

  private fun postDeliveryOf(required: Int): Long {
    val draft = QuestDraft(
      origin = QuestOrigin.BLACKBOARD,
      titleKey = "QUEST_DELIVER_TITLE",
      textArgs = listOf(QuestTextArg.number("amount", required.toLong()), QuestTextArg.item("item", IRON_ORE)),
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
  }
}
