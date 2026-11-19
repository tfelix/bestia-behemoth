package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.zone.blackboard.BoardVisit
import net.bestia.zone.economy.Commodity
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementDamage
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.quest.QuestReward
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.QuestView
import net.bestia.zone.questgen.board.TownRequestDeliveries
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.seed.DamageSeeds
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.questgen.plan.QuestPlan
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextArg
import net.bestia.zone.text.TextRef
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Hale asked for 40 iron ore at 12 coins each. A master with 30 stands at a board. */
class TownRequestDeliveriesTest {

  private val ecs = testWorld()
  private val bag = Inventory(mutableListOf(Inventory.Item(itemId = IRON, amount = 30)))
  private val master = ecs.createEntity { id -> add(id, bag) }
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(ecs) }
    every { post(any()) } answers { firstArg<World.() -> Unit>().invoke(ecs) }
  }

  private val stored = StoredQuestPlan(
    QuestPlan(
      QuestSeed(QuestSeed.Kind.TOWN_SHORTAGE, Actor(ActorType.SETTLEMENT, HALE), uniqueness = QuestSeed.Uniqueness.SHARED, need = QuestSeed.Need(IRON, 40)),
      motif = "town_shortage", title = TextRef("QUESTGEN_TOWN_SHORTAGE_TITLE", mapOf("town" to TextArg.Name(TOWN_NAME))), startsAt = WorldTarget.settlement(HALE),
      suggestedLevel = 10, steps = emptyList(), score = 1.0,
    )
  ).apply { state = StoredQuestPlan.State.OFFERED }

  private val plans = mockk<StoredQuestPlanRepository> { every { findByQuestId(QUEST) } returns stored }
  private val quests = mockk<QuestService> {
    every { advanceShared(QUEST, 0, any(), MASTER) } answers { thirdArg() }
    every { viewFor(MASTER, QUEST) } returns questView(progress = 30, status = QuestStatus.OPEN)
  }
  private val inventory = mockk<InventoryService> { every { consumeAll(MASTER, any()) } returns true }
  private val commodityItems = mockk<CommodityItems> {
    every { commodityOf(IRON) } returns Commodity("iron", "iron_ore", 10.0, 0.0, 5.0, 0.1, null)
    every { coinItemId() } returns COIN
  }
  private val economy = mockk<SettlementEconomyService> { every { takeDelivery(HALE, "iron", any(), any()) } answers { arg(3) } }
  private val offers = mockk<QuestOffers>(relaxed = true)

  private val damage = mockk<DamageSeeds> { every { destroyedBuildingsOf(HALE) } returns listOf(501L, 502L) }
  private val restoration = mockk<PropRestoration> { every { restore(any()) } returns true }
  private val capacity = mockk<SettlementDamage>(relaxed = true)

  private val deliveries = TownRequestDeliveries(plans, quests, inventory, commodityItems, economy, view, offers, damage, restoration, capacity)

  @Test
  fun `material to rebuild is paid from the purse, and the last unit raises the buildings again`() {
    val rebuilding = StoredQuestPlan(
      stored.plan.copy(seed = stored.plan.seed.copy(kind = QuestSeed.Kind.TOWN_DAMAGE, need = QuestSeed.Need(PLANK, 40))),
    ).apply { state = StoredQuestPlan.State.OFFERED }
    every { plans.findByQuestId(QUEST) } returns rebuilding
    every { commodityItems.commodityOf(PLANK) } returns null
    every { economy.payFor(HALE, any()) } answers { secondArg() }
    bag.addItem(Inventory.Item(itemId = PLANK, amount = 40))

    every { quests.viewFor(MASTER, QUEST) } returns questView(progress = 20, status = QuestStatus.OPEN, item = PLANK)
    assertNull(deliveries.handIn(visit(TOWN_NAME), questView(0, QuestStatus.OPEN, item = PLANK), 20))
    verify(exactly = 0) { restoration.restore(any()) }

    every { quests.viewFor(MASTER, QUEST) } returns questView(progress = 40, status = QuestStatus.COMPLETED, item = PLANK)
    assertNull(deliveries.handIn(visit(TOWN_NAME), questView(20, QuestStatus.OPEN, item = PLANK), 20))

    verify { economy.payFor(HALE, 240) }
    verify(exactly = 0) { economy.takeDelivery(any(), any(), any(), any()) }
    verify { restoration.restore(501L) }
    verify { restoration.restore(502L) }
    verify { capacity.invalidate() }
    assertEquals(StoredQuestPlan.State.ENDED, rebuilding.state)
  }

  @Test
  fun `the goods leave the bag for the town's stock, and the town pays from its purse`() {
    assertNull(handIn(amount = 50, at = TOWN_NAME))

    verify { inventory.consumeAll(MASTER, listOf(IRON to 30)) }
    verify { quests.advanceShared(QUEST, 0, 30, MASTER) }
    verify { economy.takeDelivery(HALE, "iron", 30, 360) }
    assertEquals(0, bag.getItems().sumOf { it.amount }, "the live bag follows the database")
    val coins = ecs.get(master, CreateItemsIntent::class)!!.grants.single()
    assertEquals(COIN to 360, coins.itemId to coins.amount)
  }

  @Test
  fun `the last unit ends the plan with the quest`() {
    every { quests.viewFor(MASTER, QUEST) } returns questView(progress = 40, status = QuestStatus.COMPLETED)

    handIn(amount = 10, at = TOWN_NAME, progress = 30)

    assertEquals(StoredQuestPlan.State.ENDED, stored.state)
  }

  @Test
  fun `a town takes its goods at its own board only, and nothing from an empty bag`() {
    assertEquals(OpError.BLACKBOARD_HAND_IN_ELSEWHERE, handIn(amount = 10, at = "Dunmere"))
    verify(exactly = 0) { inventory.consumeAll(any(), any()) }

    bag.takeAcrossPiles(IRON, 30)
    assertEquals(OpError.BLACKBOARD_NOTHING_TO_HAND_IN, handIn(amount = 10, at = TOWN_NAME))
  }

  private fun handIn(amount: Int, at: String, progress: Int = 0): OpError? {
    return deliveries.handIn(visit(at), questView(progress, QuestStatus.OPEN), amount)
  }

  private fun visit(at: String): BoardVisit {
    return BoardVisit(accountId = 1, masterId = MASTER, masterEntityId = master, masterName = "Ayla", boardEntityId = 99, placeName = at)
  }

  private fun questView(progress: Int, status: QuestStatus, item: Long = IRON): QuestView {
    val wanted = QuestView.Objective(ObjectiveKind.DELIVER_ITEM, ObjectiveScope.SHARED, item, null, null, required = 40, progress = progress, yours = 0)

    return QuestView(
      QUEST, QuestOrigin.TOWN, status, QuestView.Role.ACCEPTED, TextRef("QUESTGEN_TOWN_SHORTAGE_TITLE"), 10, Instant.MAX, TOWN_NAME,
      listOf(wanted), listOf(QuestReward(QuestReward.Kind.COINS_PER_UNIT, 12)),
    )
  }

  private companion object {
    const val QUEST = 70L
    const val MASTER = 9L
    const val HALE = 6
    const val IRON = 8L
    const val PLANK = 12L
    const val COIN = 1L
    const val TOWN_NAME = "Hale"
  }
}
