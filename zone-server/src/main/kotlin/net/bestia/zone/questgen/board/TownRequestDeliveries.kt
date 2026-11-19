package net.bestia.zone.questgen.board

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.blackboard.BoardVisit
import net.bestia.zone.blackboard.TownDeliveries
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.quest.QuestService
import net.bestia.zone.quest.QuestStatus
import net.bestia.zone.quest.QuestView
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.questgen.persistence.StoredQuestPlanRepository
import net.bestia.zone.text.TextArg
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Takes the goods a town asked for. They go into the town's own stock and the town pays from its own purse, so a
 * delivery really ends the shortage it was asked for, and no coin is minted. Off the tick.
 */
@Service
class ShortageDeliveries(
  private val plans: StoredQuestPlanRepository,
  private val quests: QuestService,
  private val inventory: InventoryService,
  private val commodityItems: CommodityItems,
  private val economy: SettlementEconomyService,
  private val world: WorldView,
  private val offers: QuestOffers,
) : TownDeliveries {

  @Transactional
  override fun handIn(visit: BoardVisit, quest: QuestView, amount: Int): OpError? {
    val stored = plans.findByQuestId(quest.questId) ?: return OpError.REQUEST_REFUSED
    val town = stored.plan.startsAt.key.toInt()
    // Every board shows the request, and only the board of the town that asked takes the goods.
    val townName = (stored.plan.title.args[TOWN_ARG] as? TextArg.Name)?.value
    if (townName != visit.placeName) {
      return OpError.BLACKBOARD_HAND_IN_ELSEWHERE
    }

    val wanted = quest.objectives.single()
    val itemId = wanted.itemId ?: return OpError.REQUEST_REFUSED
    val commodity = commodityItems.commodityOf(itemId) ?: return OpError.REQUEST_REFUSED
    val carried = world.read { looseAmountOf(this, visit, itemId) }
    val given = minOf(amount, wanted.required - wanted.progress, carried)
    if (given <= 0 || !inventory.consumeAll(visit.masterId, listOf(itemId to given))) {
      return OpError.BLACKBOARD_NOTHING_TO_HAND_IN
    }

    // Taken out of the bag, so from here the delivery counts in full or the transaction rolls back.
    check(quests.advanceShared(quest.questId, objectiveIndex = 0, amount = given, byMasterId = visit.masterId) == given) {
      "Quest ${quest.questId} did not take the $given units it still wanted"
    }
    if (quests.viewFor(visit.masterId, quest.questId)?.status == QuestStatus.COMPLETED) {
      stored.state = StoredQuestPlan.State.ENDED
    }

    val coinsEach = quest.rewards.firstOrNull()?.amount ?: 0
    afterCommit {
      world.post { settleOnTick(this, visit, town, commodity.id, itemId, given, coinsEach * given) }
      offers.refresh()
    }

    return null
  }

  /** The live bag follows the database, the town's books take the goods, and the town pays what it can. */
  private fun settleOnTick(world: World, visit: BoardVisit, town: Int, commodity: String, itemId: Long, units: Int, pay: Long) {
    world.get(visit.masterEntityId, Inventory::class)?.takeAcrossPiles(itemId, units)

    val paid = economy.takeDelivery(town, commodity, units, pay)
    val coinId = commodityItems.coinItemId() ?: return
    if (paid > 0) {
      val due = world.get(visit.masterEntityId, CreateItemsIntent::class)?.grants.orEmpty()
      world.add(visit.masterEntityId, CreateItemsIntent(due + CreateItemIntent(coinId, paid.toInt())))
    }
  }

  private fun looseAmountOf(world: World, visit: BoardVisit, itemId: Long): Int {
    val held = world.get(visit.masterEntityId, Inventory::class)?.getItems().orEmpty()

    return held.filter { it.itemId == itemId && it.isStackable }.sumOf { it.amount }
  }

  private companion object {
    /** The slot of the plan's texts that holds the name of the town that asked. */
    const val TOWN_ARG = "town"
  }
}
