package net.bestia.zone.stronghold

import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.mining.OreYield
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Every Stronghold's daily treasure. A drop is made every day after planting, sized by Economy: coins, ore of the
 * deposits round the seed, and crafting items. Gold is never in it: gold ore sizes the world's coin supply. Tick
 * thread only.
 */
@Service
class Treasury(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val lodes: SeedLodes,
  private val items: ItemTemplateRegistry,
  private val config: StrongholdConfig,
  private val clock: Clock = Clock.systemUTC(),
  private val random: Random = Random.Default,
) {

  private val interval: Duration = Duration.ofHours(config.treasuryDropHours)

  /** Makes every drop that is due. A server that was down makes them all, and keeps the newest. */
  fun makeDue() {
    val now = clock.instant()

    for (stronghold in strongholds.all().filter { it.state == State.STANDING }.toList()) {
      val due = (Duration.between(stronghold.plantedAt, now).toMillis() / interval.toMillis()).toInt()
      if (due <= stronghold.dropsMade) continue

      val seed = seeds.awake().firstOrNull { it.rank == stronghold.rank } ?: continue
      val made = (stronghold.dropsMade + 1..due).map { number -> dropOf(stronghold, seed, number) }
      val waiting = (stronghold.drops + made).takeLast(config.treasuryWaitingDrops)
      strongholds.put(stronghold.copy(dropsMade = due, drops = waiting))
    }
  }

  /** When the next drop comes. */
  fun nextDropAt(stronghold: Stronghold): Instant {
    return stronghold.plantedAt.plus(interval.multipliedBy(stronghold.dropsMade + 1L))
  }

  private fun dropOf(stronghold: Stronghold, seed: EmpyriteSeed, number: Int): TreasureDrop {
    val economy = stronghold.progressOf(StrongholdTrack.ECONOMY).level
    val coins = config.treasuryCoinsBase + config.treasuryCoinsPerEconomy * economy
    val goods = HashMap<Long, Int>()
    oreNear(seed).forEach { itemId -> goods.merge(itemId, config.treasuryOrePiecesPerEconomy * economy, Int::plus) }
    craftingItems(economy).forEach { itemId -> goods.merge(itemId, 1, Int::plus) }

    return TreasureDrop(stronghold.plantedAt.plus(interval.multipliedBy(number.toLong())), coins, goods.filterValues { it > 0 })
  }

  /** The item of each of the nearest deposit kinds round the seed, gold left out. */
  private fun oreNear(seed: EmpyriteSeed): List<Long> {
    return lodes.kindsNear(seed, config.treasuryOreRangeMetres)
      .filter { it !in GOLD }
      .mapNotNull { type -> OreYield.ITEM_BY_RESOURCE[type]?.let(items::idOf) }
      .take(config.treasuryOreKinds)
  }

  private fun craftingItems(economy: Int): List<Long> {
    val unlocked = config.treasuryCraftingItems.filter { it.fromEconomy <= economy }.mapNotNull { items.idOf(it.item) }
    if (unlocked.isEmpty()) return emptyList()

    return List(economy / config.treasuryEconomyPerCraftingItem) { unlocked.random(random) }
  }

  private companion object {
    val GOLD = setOf(ResourceType.GOLD_LODE, ResourceType.GOLD_PLACER)
  }
}
