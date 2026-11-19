package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.zone.economy.Commodity
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.SettlementMarket
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.questgen.seed.ShortageSeeds
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ShortageSeedsTest {

  private val ecs = testWorld()
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(ecs) }
  }
  private val commodityItems = mockk<CommodityItems> {
    every { priced() } returns mapOf(IRON to commodity("iron"), BREAD to commodity("bread"))
  }
  private val economy = mockk<SettlementEconomyService> { every { marketOf(any()) } returns null }
  private val worldService = mockk<WorldService> {
    every { generated.world.chronicle } returns Chronicle(
      0, 1000, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
      settlements = listOf(town(0, abandoned = 0), town(1, abandoned = 0), town(2, abandoned = 800)), prunedEvents = 0,
    )
  }

  private val seeds = ShortageSeeds(view, economy, commodityItems, worldService, QuestgenConfig())

  @Test
  fun `a town that lacks most of a good asks for part of what is missing`() {
    // Half of the iron is gone and three quarters of the bread: the bread is the worse lack.
    every { economy.marketOf(0) } returns market(mapOf("iron" to (100.0 to 50.0), "bread" to (200.0 to 150.0)))

    val seed = seeds.seeds().single()

    assertEquals(QuestSeed.Kind.TOWN_SHORTAGE to Actor(ActorType.SETTLEMENT, 0), seed.kind to seed.subject)
    assertEquals(QuestSeed.Uniqueness.SHARED, seed.uniqueness)
    assertEquals(QuestSeed.Need(BREAD, amount = 90), seed.need, "60 percent of the 150 loaves missing")
  }

  @Test
  fun `a town that is well stocked, a small lack and a ruin ask for nothing`() {
    every { economy.marketOf(0) } returns market(mapOf("iron" to (100.0 to 10.0), "bread" to (200.0 to 0.0)))
    every { economy.marketOf(1) } returns market(mapOf("iron" to (20.0 to 15.0), "bread" to (200.0 to 0.0)))
    every { economy.marketOf(2) } returns market(mapOf("iron" to (100.0 to 100.0), "bread" to (200.0 to 200.0)))

    assertEquals(emptyList(), seeds.seeds())
  }

  /** @param stock expected and missing units per commodity */
  private fun market(stock: Map<String, Pair<Double, Double>>): SettlementMarket {
    return mockk {
      stock.forEach { (commodity, numbers) ->
        every { expectedOf(commodity) } returns numbers.first
        every { shortageOf(commodity) } returns numbers.second
      }
    }
  }

  private fun commodity(id: String): Commodity {
    return Commodity(id, item = id, refPrice = 10.0, spoilPerDay = 0.0, coverDays = 5.0, perCapitaPerDay = 0.1, season = null)
  }

  private fun town(index: Int, abandoned: Int): SettlementRecord {
    return SettlementRecord(
      index, foundedYear = 100, abandonedYear = abandoned, population = 300, wealth = 1.0, ownerCiv = 0, foundingCiv = 0,
      timesSacked = 0, wallYear = 0, wallPopulation = 0, nameSeed = 7, nameCulture = 0, formerNames = emptyList(),
      ruinCause = null, sites = emptyList(),
    )
  }

  private companion object {
    const val IRON = 8L
    const val BREAD = 9L
  }
}
