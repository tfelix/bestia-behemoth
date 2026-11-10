package net.bestia.zone.treasure

import net.bestia.worldgen.treasure.TreasureTier
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldMoneySupply
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatRoller
import org.springframework.stereotype.Service
import java.util.Random

/**
 * Rolls what an opened cache holds. Rolled when it is dug, not when the world was made, so the coin can come
 * out of what the world reserve still has.
 */
@Service
class TreasureLoot(
  private val reserve: WorldReserve,
  private val money: WorldMoneySupply,
  private val commodities: CommodityItems,
  private val items: ItemTemplateRegistry,
  private val stats: ItemStatCatalog,
  private val roller: ItemStatRoller,
  private val random: Random = ThreadLocalRandomSource,
) {

  fun roll(tier: TreasureTier, excavationLevel: Int): List<CreateItemIntent> {
    val contents = ArrayList<CreateItemIntent>()
    coins(tier)?.let(contents::add)
    contents.addAll(valuables(tier))
    if (random.nextDouble() < TOP_ITEM_CHANCE + TOP_ITEM_CHANCE_PER_LEVEL * excavationLevel) {
      gear(tier)?.let(contents::add)
    }
    return contents
  }

  /** A cache prints no coin: what it gives, the reserve no longer has. It gives less as the reserve runs dry. */
  private fun coins(tier: TreasureTier): CreateItemIntent? {
    val coinItemId = commodities.coinItemId() ?: return null
    val range = COINS.getValue(tier)
    val fill = (reserve.available() / money.unminted).coerceIn(0.0, 1.0)
    val amount = (randomIn(range) * fill).toInt()
    if (amount <= 0) return null

    reserve.charge(amount.toDouble(), CoinSource.TREASURE)
    return CreateItemIntent(coinItemId, amount)
  }

  private fun valuables(tier: TreasureTier): List<CreateItemIntent> {
    val pool = VALUABLES.getValue(tier).mapNotNull(items::idOf)
    if (pool.isEmpty()) return emptyList()

    return List(randomIn(VALUABLE_COUNTS.getValue(tier))) {
      CreateItemIntent(pool[random.nextInt(pool.size)], 1)
    }
  }

  private fun gear(tier: TreasureTier): CreateItemIntent? {
    val candidates = stats.withRandomStats()
    if (candidates.isEmpty()) return null

    val itemId = candidates[random.nextInt(candidates.size)]
    return CreateItemIntent(itemId, 1, roller.roll(stats.sheetOf(itemId), GEAR_LEVEL_CAP.getValue(tier)))
  }

  private fun randomIn(range: IntRange): Int {
    return range.first + random.nextInt(range.last - range.first + 1)
  }

  private companion object {
    const val TOP_ITEM_CHANCE = 0.25
    const val TOP_ITEM_CHANCE_PER_LEVEL = 0.05

    val COINS = mapOf(
      TreasureTier.POOR to 0..50,
      TreasureTier.MODEST to 100..400,
      TreasureTier.RICH to 1_000..3_000,
      TreasureTier.SPLENDID to 5_000..15_000
    )

    val VALUABLE_COUNTS = mapOf(
      TreasureTier.POOR to 0..1,
      TreasureTier.MODEST to 1..2,
      TreasureTier.RICH to 2..3,
      TreasureTier.SPLENDID to 3..4
    )

    val VALUABLES = mapOf(
      TreasureTier.POOR to listOf("rough_gem"),
      TreasureTier.MODEST to listOf("rough_gem", "sea_pearl"),
      TreasureTier.RICH to listOf("rough_gem", "rough_ruby", "rough_emerald", "sea_pearl"),
      TreasureTier.SPLENDID to listOf("rough_ruby", "rough_emerald", "cut_ruby", "cut_emerald", "cut_diamond")
    )

    /** The highest item level the gear in a cache may roll. */
    val GEAR_LEVEL_CAP = mapOf(
      TreasureTier.POOR to 10,
      TreasureTier.MODEST to 25,
      TreasureTier.RICH to 45,
      TreasureTier.SPLENDID to 70
    )
  }
}
