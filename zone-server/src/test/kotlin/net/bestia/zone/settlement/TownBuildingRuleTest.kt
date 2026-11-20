package net.bestia.zone.settlement

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TownBuildingRuleTest {

  private val signAt = Vec3L(1000, 1000, 10)
  private val structures = mutableListOf<StructureEntry>()
  private var town: Town? = null

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

    rule = TownBuildingRule(towns, registry, worldService)
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
  fun `a chest or an unfinished site anchors nothing`() {
    town = standingTown()
    structures += structure(StaticEntityKind.CHEST, signAt.plus(80, 0))
    structures += structure(StaticEntityKind.FURNACE, signAt.plus(0, 80), buildSeconds = 20f)

    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(100, 0)))
    assertEquals(OpError.STRUCTURE_TOO_FAR_FROM_TOWN, rule.refusal(StaticEntityKind.FORGE, signAt.plus(0, 100)))
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
}
