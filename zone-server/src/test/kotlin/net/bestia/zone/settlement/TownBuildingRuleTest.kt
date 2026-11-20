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
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TownBuildingRuleTest {

  private val signAt = Vec3L(1000, 1000, 10)
  private val structures = mutableListOf<StructureEntry>()
  private var town: Town? = null
  private var generatedTown: SettlementSite? = null
  private val razedPropIds = mutableSetOf<Long>()

  private lateinit var rule: TownBuildingRule

  @BeforeEach
  fun setUp() {
    val worldService = mockk<WorldService> {
      every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    }
    val towns = mockk<TownRegistry> {
      every { standingAt(any()) } answers { town }
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
      every { siteCovering(any(), any()) } answers { generatedTown }
    }
    val restoration = mockk<PropRestoration> {
      every { isDestroyed(any()) } answers { firstArg<Long>() in razedPropIds }
    }

    rule = TownBuildingRule(towns, registry, generatedTowns, restoration, worldService)
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
    town = standingTown()

    assertNull(rule.refusal(StaticEntityKind.FORGE, signAt.plus(25, 0)))
  }

  @Test
  fun `a town structure far from the sign and every building is refused`() {
    town = standingTown()

    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(100, 0)))
  }

  @Test
  fun `a finished building anchors the next one`() {
    town = standingTown()
    structures += structure(StaticEntityKind.FURNACE, signAt.plus(80, 0))

    assertNull(rule.refusal(StaticEntityKind.FORGE, signAt.plus(100, 0)))
  }

  @Test
  fun `a wild structure or an unfinished site anchors nothing`() {
    town = standingTown()
    structures += structure(StaticEntityKind.WORKBENCH, signAt.plus(80, 0))
    structures += structure(StaticEntityKind.FURNACE, signAt.plus(0, 80), buildSeconds = 20f)

    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(100, 0)))
    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(0, 100)))
  }

  @Test
  fun `a town structure near a generated building is allowed`() {
    generatedTown = hamlet()

    assertNull(rule.refusal(StaticEntityKind.FORGE, Vec3L(HOUSE_X + 20, 0, 10)))
  }

  @Test
  fun `a town structure near a razed generated building is refused`() {
    generatedTown = hamlet()
    razedPropIds += HOUSE_PROP_ID

    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, Vec3L(HOUSE_X + 20, 0, 10)))
  }

  @Test
  fun `a player building in a generated town anchors the next one`() {
    generatedTown = hamlet()
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
      index = 1,
      centre = Vec2d(0.0, 0.0),
      tier = SettlementTier.HAMLET,
      population = null,
      unordered = listOf(house),
    )
  }

  private fun standingTown(): Town {
    return Town(
      id = 1L,
      name = "Oakhold",
      founderMasterId = 3L,
      founderAccountId = 7L,
      founderName = "Ada",
      sign = signAt,
      state = PlayerSettlement.State.STANDING,
      siteStructureId = null,
      siteWorkedAt = Instant.EPOCH,
      signHp = 2_000,
      fellAt = null,
    )
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
  }
}
