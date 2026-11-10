package net.bestia.zone.postal.office

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.settlement.Town
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostOfficesPlayerTest {

  private val sign = Vec3L(1000, 1000, 10)
  private val officeId = PostOffices.PLAYER_OFFICE_BASE + 4
  private var town = Town(
    id = 4, name = "Oakhold", founderMasterId = 3, founderAccountId = 7, founderName = "Ada", sign = sign,
    state = PlayerSettlement.State.STANDING, siteStructureId = null, siteWorkedAt = Instant.EPOCH,
    signHp = 2000, fellAt = null,
  )
  private val structures = mutableListOf<StructureEntry>()

  private val towns = mockk<TownRegistry> {
    every { of(4L) } answers { town }
    every { standingAt(any()) } answers { town.takeIf { it.isStanding } }
    every { metresBetween(any(), any()) } answers {
      val a = firstArg<Vec3L>()
      val b = secondArg<Vec3L>()
      Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
    }
  }
  private val worldService = mockk<WorldService> {
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
  }
  private val registry = mockk<PlayerStructureRegistry> { every { all() } answers { structures } }

  private val offices = PostOffices(worldService, mockk(), mockk(), towns, registry)

  @Test
  fun `a town without a finished post office has no office`() {
    structures += StructureEntry(1, StaticEntityKind.POST_OFFICE, 3, 7, sign, 0f, 1800f, 1800f)

    assertNull(offices.officeOf(officeId))
  }

  @Test
  fun `a finished post office in a standing town is a level 1 office named after the town`() {
    structures += StructureEntry(1, StaticEntityKind.POST_OFFICE, 3, 7, Vec3L(1020, 1000, 10), 0f)

    assertEquals(PostOffices.Office(officeId, "Oakhold", 1), offices.officeOf(officeId))
    assertEquals(PostOffices.Office(officeId, "Oakhold", 1), offices.playerOfficeAt(Vec3L(1020, 1000, 10)))
    assertEquals(Vec3L(1020, 1000, 10), offices.doorOf(officeId))
  }

  @Test
  fun `the office of a fallen town is closed`() {
    structures += StructureEntry(1, StaticEntityKind.POST_OFFICE, 3, 7, Vec3L(1020, 1000, 10), 0f)
    town = town.copy(state = PlayerSettlement.State.FALLEN)

    assertNull(offices.officeOf(officeId))
  }
}
