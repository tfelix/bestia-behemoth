package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.voxel.PropId
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.persistence.WorldObjectDivergenceRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementFateServiceTest {

  private val worldService = mockk<WorldService> {
    every { config } returns WorldConfig(seed = 1L, widthCells = 8, heightCells = 8)
    every { record } returns mockk {
      every { pipelineVersion } returns 1L
      every { shapeVersion } returns 1L
    }
  }

  // Runs each write inline, so a depletion is recorded and told to listeners as on the tick.
  private val divergence = WorldObjectDivergenceRegistry(
    mockk<WorldObjectDivergenceRepository>(relaxed = true) { every { save(any()) } answers { firstArg() } },
    mockk<AsyncJobExecutor> { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } },
    worldService
  )

  private val residency = mockk<WorldObjectResidencyService> {
    every { entitiesIn(any(), any()) } returns LongArray(0)
  }

  private val village = SettlementSite(
    index = VILLAGE,
    centre = Vec2d(10.0, 10.0),
    tier = SettlementTier.VILLAGE,
    population = null,
    unordered = listOf(
      building(HOUSE_A, BuildingFunction.RESIDENCE),
      building(HOUSE_B, BuildingFunction.RESIDENCE),
      building(TOWER, BuildingFunction.FORTIFICATION),
    )
  )

  private val sites = mockk<SettlementSiteIndex> {
    every { siteOf(VILLAGE) } returns village
    every { siteCovering(any(), any()) } returns village
  }

  private fun building(propId: Long, function: BuildingFunction): SettlementSite.Building {
    return SettlementSite.Building(propId, function, Vec2d(10.0, 10.0), Vec2d(10.0, 11.0), 0.0, -1)
  }

  private fun destroy(propId: Long) {
    divergence.recordDepletion(propId, StaticEntityKind.BUILDING_RESIDENCE, resumeAt = null)
  }

  @Test
  fun `a settlement falls with its last building, and a fortification never counts`() {
    val sut = SettlementFateService(divergence, sites, residency, worldService)
    val fell = ArrayList<Int>()
    sut.onFell { fell.add(it) }

    destroy(HOUSE_A)
    assertFalse(sut.hasFallen(VILLAGE), "one house still stands")

    destroy(HOUSE_B)
    assertTrue(sut.hasFallen(VILLAGE))
    assertEquals(listOf(VILLAGE), fell)
  }

  @Test
  fun `a building that will grow back has not fallen`() {
    val sut = SettlementFateService(divergence, sites, residency, worldService)

    destroy(HOUSE_A)
    divergence.recordDepletion(HOUSE_B, StaticEntityKind.BUILDING_RESIDENCE, resumeAt = Instant.now())

    assertFalse(sut.hasFallen(VILLAGE))
  }

  @Test
  fun `a settlement that fell before this run is found at boot`() {
    destroy(HOUSE_A)
    destroy(HOUSE_B)

    val sut = SettlementFateService(divergence, sites, residency, worldService)
    sut.deriveAll()

    assertEquals(setOf(VILLAGE), sut.fallen())
  }

  @Test
  fun `razing destroys every standing building and the settlement falls`() {
    val sut = SettlementFateService(divergence, sites, residency, worldService)
    destroy(HOUSE_A)

    assertEquals(1, sut.raze(testWorld(), VILLAGE))
    assertTrue(sut.hasFallen(VILLAGE))
  }

  private companion object {
    const val VILLAGE = 4
    val HOUSE_A = PropId.of(PropKind.BUILDING, 10, 10)
    val HOUSE_B = PropId.of(PropKind.BUILDING, 12, 10)
    val TOWER = PropId.of(PropKind.BUILDING, 14, 10)
  }
}
