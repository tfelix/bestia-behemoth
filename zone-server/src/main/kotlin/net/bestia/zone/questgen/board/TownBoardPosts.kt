package net.bestia.zone.questgen.board

import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.questgen.offer.QuestOffers
import net.bestia.zone.questgen.persistence.StoredQuestPlan
import net.bestia.zone.townsfolk.knowledge.ChronicleNames
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/** Puts what a town asks for on the boards, at a price the town sets from its own market. */
@Component
class TownBoardPosts(
  private val offers: QuestOffers,
  private val economy: SettlementEconomyService,
  private val commodityItems: CommodityItems,
  private val worldService: WorldService,
  private val world: WorldView,
  private val config: QuestgenConfig,
) {

  /** Whether the plan is offered on the boards and not by a townsperson. */
  fun isForBoard(stored: StoredQuestPlan): Boolean {
    return stored.plan.startsAt.kind == WorldTarget.Kind.SETTLEMENT
  }

  /** @return false when the town has no market or no name any more, so nothing was posted */
  fun post(stored: StoredQuestPlan): Boolean {
    val town = stored.plan.startsAt.key.toInt()
    val commodity = stored.plan.seed.need?.let { commodityItems.commodityOf(it.itemId) } ?: return false
    val name = ChronicleNames.placeOf(worldService.generated.world.chronicle, town) ?: return false
    // The books of the towns are kept on the tick.
    val price = world.read { economy.marketOf(town)?.priceOf(commodity.id) } ?: return false

    offers.postOnBoard(stored, coinsEach = (price * config.shortagePriceFactor).roundToLong().coerceAtLeast(1), townName = name)

    return true
  }
}
