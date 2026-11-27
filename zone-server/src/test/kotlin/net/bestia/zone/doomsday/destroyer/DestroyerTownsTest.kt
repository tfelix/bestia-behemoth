package net.bestia.zone.doomsday.destroyer

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.doomsday.destroyer.DestroyerTowns.Target
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.settlement.Town
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.settlement.TownService
import net.bestia.zone.settlement.ecs.CitySign
import net.bestia.zone.settlement.persistence.PlayerSettlement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DestroyerTownsTest {

  private val ecs = testWorld()
  private var town = Town(
    id = TOWN, name = "Oakhold", founderMasterId = 3, founderAccountId = 7, founderName = "Ada", sign = Vec3L(1000, 0, 10),
    state = PlayerSettlement.State.STANDING, siteStructureId = null, siteWorkedAt = Instant.EPOCH,
    signHp = 2000, fellAt = null,
  )
  private val townService = mockk<TownService>(relaxed = true)
  private var sanctuary = false

  private val towns = DestroyerTowns(
    worldService = mockk { every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0) },
    fates = mockk(relaxed = true),
    playerTowns = mockk<TownRegistry> { every { of(TOWN) } answers { town } },
    townService = townService,
    startTowns = mockk(relaxed = true),
    provinces = mockk<ProvinceService> { every { isSanctuaryAt(any(), any()) } answers { sanctuary } },
    eventGround = mockk(relaxed = true),
  )

  @Test
  fun `it marches on the sign of a player town`() {
    assertTrue(towns.isTarget(Target.PlayerTown(TOWN), emptyList()))
    assertEquals(Vec3L(1000, 0, 10), towns.groundAt(Target.PlayerTown(TOWN)))
    assertEquals(100.0, towns.metresTo(Target.PlayerTown(TOWN), 900.0, 0.0), 1e-9)
  }

  @Test
  fun `a player town in a sanctuary, or one already fallen, is spared`() {
    sanctuary = true
    assertFalse(towns.isTarget(Target.PlayerTown(TOWN), emptyList()))

    sanctuary = false
    town = town.copy(state = PlayerSettlement.State.FALLEN)
    assertFalse(towns.isTarget(Target.PlayerTown(TOWN), emptyList()))
  }

  @Test
  fun `a player town it reaches falls through its broken sign`() {
    val sign = ecs.createEntity { id -> add(id, CitySign(TOWN)) }

    towns.raze(ecs, Target.PlayerTown(TOWN))

    assertTrue(ecs.has(sign, Dead::class))
    verify(exactly = 0) { townService.fall(any()) }
  }

  @Test
  fun `a player town nobody sees falls all the same`() {
    towns.raze(ecs, Target.PlayerTown(TOWN))

    verify { townService.fall(TOWN) }
  }

  private companion object {
    const val TOWN = 4L
  }
}
