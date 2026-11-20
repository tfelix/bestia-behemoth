package net.bestia.zone.settlement

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.StructureNeighbourhood
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WildSpacingRuleTest {

  private val structures = mutableListOf<StructureEntry>()
  private var isInSettlement = false

  private lateinit var rule: WildSpacingRule

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
    val townBuildingRule = mockk<TownBuildingRule> {
      every { isInSettlement(any()) } answers { isInSettlement }
    }

    rule = WildSpacingRule(townBuildingRule, StructureNeighbourhood(registry, worldService))
  }

  @Test
  fun `another master's camp close by is refused`() {
    structures += campfire(OTHER_MASTER, CAMP.plus(8))

    assertEquals(OpError.STRUCTURE_TOO_CLOSE, rule.refusal(StaticEntityKind.CHEST, MASTER, CAMP))
  }

  @Test
  fun `another master's camp further away is allowed`() {
    structures += campfire(OTHER_MASTER, CAMP.plus(12))

    assertNull(rule.refusal(StaticEntityKind.CHEST, MASTER, CAMP))
  }

  @Test
  fun `a master's own camp may stand close together`() {
    structures += campfire(MASTER, CAMP.plus(2))

    assertNull(rule.refusal(StaticEntityKind.CHEST, MASTER, CAMP))
  }

  @Test
  fun `inside a settlement there is no gap`() {
    isInSettlement = true
    structures += campfire(OTHER_MASTER, CAMP.plus(2))

    assertNull(rule.refusal(StaticEntityKind.FORGE, MASTER, CAMP))
  }

  private fun campfire(ownerMasterId: Long, at: Vec3L): StructureEntry {
    return StructureEntry(structures.size + 100L, StaticEntityKind.CAMPFIRE, ownerMasterId, 9L, at, 0f)
  }

  private fun Vec3L.plus(dx: Long): Vec3L {
    return Vec3L(x + dx, y, z)
  }

  private companion object {
    const val MASTER = 3L
    const val OTHER_MASTER = 4L
    val CAMP = Vec3L(5000, 5000, 10)
  }
}
