package net.bestia.zone.compendium.ecs

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.compendium.CompendiumRecorder
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test

class CompendiumSightingSystemTest {

  private val aoi = EntityAOIService()
  private val recorder = mockk<CompendiumRecorder>(relaxed = true)
  private val world = testWorld(systems = listOf(CompendiumSightingSystem(aoi, recorder)))

  @Test
  fun `a master records a wild bestia in sight`() {
    masterAt(HOME)
    bestiaAt(HOME + Vec3L(10, 0, 0), SPECIES_ID)

    world.tick(SECOND)

    verify(exactly = 1) { recorder.sighted(ACCOUNT_ID, MASTER_ID, SPECIES_ID) }
  }

  @Test
  fun `an owned bestia far from its master records for that master`() {
    masterAt(HOME)
    val away = HOME + Vec3L(500, 0, 0)
    world.createEntity { id ->
      add(id, Position.fromVec3(away))
      add(id, Account(ACCOUNT_ID))
      add(id, OwnedBestia(MASTER_ID, 1L))
    }
    bestiaAt(away + Vec3L(0, 5, 0), SPECIES_ID)

    world.tick(SECOND)

    verify(exactly = 1) { recorder.sighted(ACCOUNT_ID, MASTER_ID, SPECIES_ID) }
  }

  @Test
  fun `a bestia beyond sight is not recorded`() {
    masterAt(HOME)
    bestiaAt(HOME + Vec3L(CompendiumSightingSystem.SIGHT_VOXELS + 5, 0, 0), SPECIES_ID)

    world.tick(SECOND)

    verify(exactly = 0) { recorder.sighted(any(), any(), any()) }
  }

  @Test
  fun `owned and dead bestias are not wild`() {
    masterAt(HOME)
    val owned = bestiaAt(HOME + Vec3L(3, 0, 0), SPECIES_ID)
    world.add(owned, Account(OTHER_ACCOUNT_ID))
    val dead = bestiaAt(HOME + Vec3L(4, 0, 0), SPECIES_ID)
    world.add(dead, Dead())

    world.tick(SECOND)

    verify(exactly = 0) { recorder.sighted(any(), any(), any()) }
  }

  private fun masterAt(at: Vec3L) {
    world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Account(ACCOUNT_ID))
      add(id, Master(MASTER_ID))
    }
  }

  /** The AOI index is fed by ZoneEngine's dirty-position pass, which no test world runs. */
  private fun bestiaAt(at: Vec3L, speciesId: Long): EntityId {
    val id = world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.BESTIA, speciesId))
    }
    aoi.setEntityPosition(id, at)

    return id
  }

  private companion object {
    val HOME = Vec3L(100, 100, 10)
    const val SPECIES_ID = 1L
    const val ACCOUNT_ID = 3L
    const val OTHER_ACCOUNT_ID = 4L
    const val MASTER_ID = 7L
    const val SECOND = 1f
  }
}
