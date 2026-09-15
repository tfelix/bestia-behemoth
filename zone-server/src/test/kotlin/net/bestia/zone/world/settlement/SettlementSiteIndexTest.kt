package net.bestia.zone.world.settlement

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.zone.ecs.spawn.ambient.StandingSettlements
import net.bestia.zone.world.WorldGenConfig
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The join against the world the dev server actually generates.
 *
 * `DoorPointTest` proves the geometry against hand-built rectangles; this proves the parts that only a real
 * town has - that buildings carry the settlement they belong to, that a trade lands on the building the
 * generator put it in, and that the propId a building is named by is the one its prop will carry.
 */
class SettlementSiteIndexTest {

  private val worldService: WorldService = mockk<WorldService>().also {
    every { it.generated } returns generated
    every { it.config } returns config
  }

  private val sut = SettlementSiteIndex(worldService)

  /** The largest standing settlement, so there is a real street plan rather than a few huts. */
  private val town = biggestSettlement()

  @Test
  fun `a town has buildings and they belong to it`() {
    val site = sut.siteOf(town.index)

    assertNotNull(site)
    assertTrue(
      site!!.buildings.isNotEmpty(),
      "settlement ${town.index} (${town.tier}) resolved no buildings, so the SETTLEMENT channel join is wrong"
    )
    assertEquals(town.tier, site.tier)
  }

  @Test
  fun `every doorstep is outside its own building`() {
    val site = sut.siteOf(town.index)!!

    for (building in site.buildings) {
      val dx = building.door.x - building.centre.x
      val dy = building.door.y - building.centre.y
      assertTrue(
        dx * dx + dy * dy > 0.0,
        "a ${building.function} door sits on its own centre, so the bearing was read as a position"
      )
    }
  }

  @Test
  fun `a building is named by the propId its prop will carry`() {
    val site = sut.siteOf(town.index)!!
    val ids = site.buildings.map { it.propId }

    assertEquals(
      ids.size,
      ids.toSet().size,
      "two buildings share a propId, so the naming lattice is not injective and divergence would confuse them"
    )
    for (building in site.buildings) {
      assertSame(building, site.buildingOf(building.propId))
    }
  }

  @Test
  fun `trades land on real buildings`() {
    val site = sut.siteOf(town.index)!!
    val occupied = site.buildings.filter { it.businessType != SettlementSiteIndex.NO_BUSINESS }

    assertTrue(
      occupied.isNotEmpty(),
      "no building in a ${town.tier} houses a trade, so the business-to-building join found nothing"
    )
    for (building in occupied) {
      assertTrue(
        building.businessType in BusinessCatalogue.ALL.indices,
        "building ${building.propId} claims trade ${building.businessType}, which is not in the catalogue"
      )
    }
    assertTrue(
      occupied.size <= site.buildings.size,
      "more trades than buildings, so a business was joined to more than one host"
    )
  }

  @Test
  fun `a settlement is built once and kept`() {
    assertSame(sut.siteOf(town.index), sut.siteOf(town.index))
  }

  @Test
  fun `standing in a town finds it, and open country finds nothing`() {
    val found = sut.siteCovering(town.x.toLong(), town.y.toLong())

    assertEquals(town.index, found?.index, "standing in the middle of a town did not resolve to it")

    val far = (town.footprintRadius + 50_000).toLong()
    assertNull(
      sut.siteCovering(town.x.toLong() + far, town.y.toLong()),
      "ground $far m from the nearest town resolved to a settlement"
    )
  }

  @Test
  fun `a settlement history never founded has no site`() {
    val standing = (0 until 4096).filter { settlements.entryOf(it) != null }.toSet()
    val missing = (0 until 4096).first { it !in standing }

    assertNull(sut.siteOf(missing))
  }

  private fun biggestSettlement(): StandingSettlements.Entry {
    val found = LinkedHashSet<StandingSettlements.Entry>()
    val extent = settings.widthCells * settings.cellSizeMetres
    var y = 0.0
    while (y < extent) {
      var x = 0.0
      while (x < extent) {
        found.addAll(settlements.coveringWithin(x, y, 0.0))
        x += PROBE_METRES
      }
      y += PROBE_METRES
    }
    return requireNotNull(found.maxByOrNull { it.footprintRadius }) {
      "the dev world has no standing settlement to inspect"
    }
  }

  private companion object {
    /** `application.yml`'s pinned seed, so this measures the world the dev server actually runs. */
    const val DEV_SEED = 11_753_242L

    /** Coarse enough to sweep a 128 km world quickly, fine enough to land inside a village's footprint. */
    const val PROBE_METRES = 250.0

    private val settings = WorldGenConfig()

    val config = WorldConfig(
      seed = DEV_SEED,
      widthCells = settings.widthCells,
      heightCells = settings.heightCells,
      baseResolution = Resolution(settings.cellSizeMetres),
      seaLevel = settings.seaLevelMetres,
      chunkSize = settings.chunkSize,
      chunkHeight = settings.chunkHeight,
      voxelSize = settings.voxelSizeMetres,
      wrapX = settings.wrapX,
      wrapY = settings.wrapY
    )

    // In the companion rather than in a property: JUnit builds a fresh test instance per method, so a world
    // held per-instance is generated again for every test in the class. Read-only here, as everywhere.
    val generated = StandardWorld.build(config)
    val settlements = StandingSettlements.of(generated)
  }
}
