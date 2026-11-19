package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.SettlementRecord
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.prop.DivergenceEntry
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.prop.persistence.DivergenceState
import net.bestia.zone.questgen.seed.DamageSeeds
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DamageSeedsTest {

  private val ecs = testWorld()
  private val view = mockk<WorldView> {
    every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(ecs) }
  }
  private val gone = HashMap<Long, DivergenceEntry>()
  private val divergence = mockk<WorldObjectDivergenceRegistry> {
    every { all() } returns gone
    every { of(any()) } answers { gone[firstArg()] }
  }
  private val sites = mockk<SettlementSiteIndex> {
    every { siteOf(HALE) } returns mockk { every { buildings } returns listOf(building(501), building(502), building(503)) }
    every { siteOf(DUNMERE) } returns mockk { every { buildings } returns listOf(building(601)) }
  }
  private val items = mockk<ItemTemplateRegistry> { every { idOf("plank") } returns PLANK }
  private val worldService = mockk<WorldService> {
    every { generated.world.chronicle } returns Chronicle(
      0, 1000, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
      settlements = listOf(town(HALE), town(DUNMERE)), prunedEvents = 0,
    )
  }

  private val seeds = DamageSeeds(view, divergence, PropRestoration(divergence), sites, items, worldService, QuestgenConfig())

  @Test
  fun `a town asks for the material of every building that was destroyed`() {
    gone[501] = DivergenceEntry(BUILDING, DivergenceState.DEPLETED, resumeAt = null)
    gone[503] = DivergenceEntry(BUILDING, DivergenceState.DEPLETED, resumeAt = null)

    val seed = seeds.seeds().single()

    assertEquals(QuestSeed.Kind.TOWN_DAMAGE to Actor(ActorType.SETTLEMENT, HALE), seed.kind to seed.subject)
    assertEquals(QuestSeed.Need(PLANK, amount = 40), seed.need, "20 planks for each of the two buildings")
  }

  @Test
  fun `a world where no building is destroyed looks at no town`() {
    gone[9_000] = DivergenceEntry(StaticEntityKind.entries.first { !it.isBuilding }, DivergenceState.STUMP, resumeAt = null)

    assertEquals(emptyList(), seeds.seeds())
  }

  private fun building(propId: Long): net.bestia.zone.world.settlement.SettlementSite.Building {
    return mockk { every { this@mockk.propId } returns propId }
  }

  private fun town(index: Int): SettlementRecord {
    return SettlementRecord(
      index, foundedYear = 100, abandonedYear = 0, population = 300, wealth = 1.0, ownerCiv = 0, foundingCiv = 0,
      timesSacked = 0, wallYear = 0, wallPopulation = 0, nameSeed = 7, nameCulture = 0, formerNames = emptyList(),
      ruinCause = null, sites = emptyList(),
    )
  }

  private companion object {
    const val HALE = 0
    const val DUNMERE = 1
    const val PLANK = 12L
    val BUILDING = StaticEntityKind.entries.first { it.isBuilding }
  }
}
