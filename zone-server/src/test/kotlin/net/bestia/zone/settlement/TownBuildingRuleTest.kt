package net.bestia.zone.settlement

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.AreaNameRegistry
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.StructureNeighbourhood
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TownBuildingRuleTest {

  private val signAt = Vec3L(1000, 1000, 10)
  private val structures = mutableListOf<StructureEntry>()
  private val areaNames = AreaNameRegistry()
  private val razedPropIds = mutableSetOf<Long>()

  private lateinit var rule: TownBuildingRule

  @BeforeEach
  fun setUp() {
    val worldService = mockk<WorldService> {
      every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    }
    val registry = mockk<PlayerStructureRegistry> {
      every { `in`(any(), any()) } answers {
        structures.filter {
          Math.floorDiv(it.position.x, 32L).toInt() == firstArg<Int>() &&
              Math.floorDiv(it.position.y, 32L).toInt() == secondArg<Int>()
        }
      }
    }

    val generatedTowns = mockk<SettlementSiteIndex> {
      every { siteOf(HAMLET_INDEX) } returns hamlet()
    }
    val restoration = mockk<PropRestoration> {
      every { isDestroyed(any()) } answers { firstArg<Long>() in razedPropIds }
    }

    rule = TownBuildingRule(areaNames, StructureNeighbourhood(registry, worldService), generatedTowns, restoration, worldService)
  }

  @Test
  fun `a wild structure may stand anywhere`() {
    assertNull(rule.refusal(StaticEntityKind.CAMPFIRE, Vec3L(9000, 9000, 10)))
  }

  @Test
  fun `a town structure in the open is refused`() {
    assertEquals(OpError.STRUCTURE_NEEDS_SETTLEMENT, rule.refusal(StaticEntityKind.FORGE, Vec3L(9000, 9000, 10)))
  }

  @Test
  fun `the city sign has rules of its own`() {
    assertNull(rule.refusal(StaticEntityKind.CITY_SIGN, Vec3L(9000, 9000, 10)))
  }

  @Test
  fun `a town structure near the sign is allowed`() {
    standingTown()

    assertNull(rule.refusal(StaticEntityKind.FORGE, signAt.plus(25, 0)))
  }

  @Test
  fun `a town structure far from the sign and every building is refused`() {
    standingTown()

    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(100, 0)))
  }

  @Test
  fun `a finished building anchors the next one`() {
    standingTown()
    structures += structure(StaticEntityKind.FURNACE, signAt.plus(80, 0))

    assertNull(rule.refusal(StaticEntityKind.FORGE, signAt.plus(100, 0)))
  }

  @Test
  fun `a wild structure or an unfinished site anchors nothing`() {
    standingTown()
    structures += structure(StaticEntityKind.WORKBENCH, signAt.plus(80, 0))
    structures += structure(StaticEntityKind.FURNACE, signAt.plus(0, 80), buildSeconds = 20f)

    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(100, 0)))
    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(0, 100)))
  }

  @Test
  fun `a town structure near a generated building is allowed`() {
    generatedHamlet()

    assertNull(rule.refusal(StaticEntityKind.FORGE, Vec3L(HOUSE_X + 20, 0, 10)))
  }

  @Test
  fun `a town structure near a generated town's centre is allowed`() {
    generatedHamlet()

    assertNull(rule.refusal(StaticEntityKind.FORGE, Vec3L(20, 0, 10)))
  }

  @Test
  fun `a town structure near a razed generated building is refused`() {
    generatedHamlet()
    razedPropIds += HOUSE_PROP_ID

    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, Vec3L(HOUSE_X + 20, 0, 10)))
  }

  @Test
  fun `a player building in a generated town anchors the next one`() {
    generatedHamlet()
    structures += structure(StaticEntityKind.FURNACE, Vec3L(HOUSE_X + 25, 0, 10))

    assertNull(rule.refusal(StaticEntityKind.FORGE, Vec3L(HOUSE_X + 50, 0, 10)))
  }

  @Test
  fun `beyond a generated town's footprint a player building anchors nothing`() {
    structures += structure(StaticEntityKind.FURNACE, Vec3L(9000, 9000, 10))

    assertEquals(OpError.STRUCTURE_NEEDS_SETTLEMENT, rule.refusal(StaticEntityKind.FORGE, Vec3L(9010, 9000, 10)))
  }

  private fun hamlet(): SettlementSite {
    val house = SettlementSite.Building(
      propId = HOUSE_PROP_ID,
      function = BuildingFunction.RESIDENCE,
      centre = Vec2d(HOUSE_X.toDouble(), 0.0),
      door = Vec2d(HOUSE_X.toDouble(), 0.0),
      floorElevation = 0.0,
      businessType = SettlementSiteIndex.NO_BUSINESS,
    )

    return SettlementSite(
      index = HAMLET_INDEX,
      centre = Vec2d(0.0, 0.0),
      tier = SettlementTier.HAMLET,
      population = null,
      unordered = listOf(house),
    )
  }

  private fun standingTown() {
    areaNames.add(AreaNameRegistry.Entry(id = 1L, name = "Oakhold", x = signAt.x, y = signAt.y, radius = 150))
  }

  private fun generatedHamlet() {
    val id = AreaNameRegistry.generatedIdOf(HAMLET_INDEX)
    areaNames.add(AreaNameRegistry.Entry(id = id, name = "Elm Vale", x = 0, y = 0, radius = 200))
  }

  private fun structure(kind: StaticEntityKind, at: Vec3L, buildSeconds: Float = 0f): StructureEntry {
    return StructureEntry(structures.size + 100L, kind, 9L, 9L, at, 0f, buildSeconds, buildSeconds)
  }

  private fun Vec3L.plus(dx: Long, dy: Long): Vec3L {
    return Vec3L(x + dx, y + dy, z)
  }

  private companion object {
    const val HOUSE_X = 100L
    const val HOUSE_PROP_ID = 500L
    const val HAMLET_INDEX = 1
  }
}
