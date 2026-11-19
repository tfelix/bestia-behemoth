package net.bestia.zone.questgen.board

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.economy.SettlementDamage
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.questgen.seed.DamageSeeds
import net.bestia.zone.questgen.seed.QuestSeed
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
 * Takes the goods a town asked for, and the town pays from its own purse, so no coin is minted. What the goods
 * do depends on why the town asked: goods it lacked go into its stock, and material to rebuild raises its
 * destroyed buildings again once all of it is there. Off the tick.
 */
@Service
class TownRequestDeliveries(
  private val plans: StoredQuestPlanRepository,
  private val quests: QuestService,
  private val inventory: InventoryService,
  private val commodityItems: CommodityItems,
  private val economy: SettlementEconomyService,
  private val world: WorldView,
  private val offers: QuestOffers,
  private val damage: DamageSeeds,
  private val restoration: PropRestoration,
  private val capacity: SettlementDamage,
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
    val carried = world.read { looseAmountOf(this, visit, itemId) }
    val given = minOf(amount, wanted.required - wanted.progress, carried)
    if (given <= 0 || !inventory.consumeAll(visit.masterId, listOf(itemId to given))) {
      return OpError.BLACKBOARD_NOTHING_TO_HAND_IN
    }

    // Taken out of the bag, so from here the delivery counts in full or the transaction rolls back.
    check(quests.advanceShared(quest.questId, objectiveIndex = 0, amount = given, byMasterId = visit.masterId) == given) {
      "Quest ${quest.questId} did not take the $given units it still wanted"
    }
    val complete = quests.viewFor(visit.masterId, quest.questId)?.status == QuestStatus.COMPLETED
    if (complete) {
      stored.state = StoredQuestPlan.State.ENDED
    }

    val pay = (quest.rewards.firstOrNull()?.amount ?: 0) * given
    val rebuilds = complete && stored.plan.seed.kind == QuestSeed.Kind.TOWN_DAMAGE
    afterCommit {
      world.post {
        settleOnTick(this, visit, town, itemId, given, pay)
        if (rebuilds) {
          rebuild(town)
        }
      }
      offers.refresh()
    }

    return null
  }

  /** With all the material there, every destroyed building of the town stands again. Tick thread. */
  private fun rebuild(town: Int) {
    val raised = damage.destroyedBuildingsOf(town).count(restoration::restore)
    // The town's trades were counted with the buildings missing.
    capacity.invalidate()
    LOG.info { "Settlement $town rebuilt $raised buildings" }
  }

  /** The live bag follows the database, and the town pays what it can. Goods it lacked go into its stock. */
  private fun settleOnTick(world: World, visit: BoardVisit, town: Int, itemId: Long, units: Int, pay: Long) {
    world.get(visit.masterEntityId, Inventory::class)?.takeAcrossPiles(itemId, units)

    val commodity = commodityItems.commodityOf(itemId)
    val paid = if (commodity == null) economy.payFor(town, pay) else economy.takeDelivery(town, commodity.id, units, pay)
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
    val LOG = KotlinLogging.logger { }

    /** The slot of the plan's texts that holds the name of the town that asked. */
    const val TOWN_ARG = "town"
  }
}
