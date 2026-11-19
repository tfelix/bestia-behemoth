package net.bestia.zone.questgen.seed

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.questgen.QuestgenConfig
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.ceil

/**
 * A town that really lacks a good: its stock is far below what it should hold. That happens when players bought
 * it empty or its workplaces were destroyed, never by itself. One seed per town, for the good it lacks most.
 */
@Component
class ShortageSeeds(
  private val world: WorldView,
  private val economy: SettlementEconomyService,
  private val commodityItems: CommodityItems,
  private val worldService: WorldService,
  private val config: QuestgenConfig,
) : QuestSeedSource {

  override fun seeds(): List<QuestSeed> {
    val chronicle = worldService.generated.world.chronicle
    val standing = chronicle.settlements.filter { chronicle.settlementStood(it.index, chronicle.presentYear) }.map { it.index }

    // The books of the towns are kept on the tick.
    return world.read { standing.mapNotNull { worstShortageOf(it) } }
  }

  private fun worstShortageOf(settlement: Int): QuestSeed? {
    val market = economy.marketOf(settlement) ?: return null
    val worst = commodityItems.priced().entries
      .map { (itemId, commodity) -> Triple(itemId, market.shortageOf(commodity.id), market.expectedOf(commodity.id)) }
      .filter { (_, missing, expected) -> missing >= config.shortageMinUnits && missing >= expected * config.shortageShare }
      .maxByOrNull { (_, missing, expected) -> missing / expected }
      ?: return null

    val (itemId, missing, _) = worst
    val asked = ceil(missing * config.shortageAskedShare).toInt().coerceAtLeast(1)

    return QuestSeed(
      QuestSeed.Kind.TOWN_SHORTAGE,
      Actor(ActorType.SETTLEMENT, settlement),
      uniqueness = QuestSeed.Uniqueness.SHARED,
      need = QuestSeed.Need(itemId, asked),
    )
  }
}
