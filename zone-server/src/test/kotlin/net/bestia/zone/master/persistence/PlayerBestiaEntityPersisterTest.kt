package net.bestia.zone.master.persistence

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.account.SavePointService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerBestiaEntityPersisterTest {

  private val masterId = 5L
  private val playerBestiaId = 7L
  private val savePoint = Vec3L(10, 20, 30)

  private val row = PlayerBestia(master = mockk(relaxed = true), bestia = mockk(relaxed = true))

  private val repository = mockk<PlayerBestiaRepository> {
    every { findByIdForUpdate(playerBestiaId) } returns row
    every { save(any()) } returns row
  }

  private val savePoints = mockk<SavePointService> {
    every { forPlayerBestia(playerBestiaId) } returns savePoint
  }

  private val sut = PlayerBestiaEntityPersister(repository, savePoints)

  private fun World.playerBestia(dead: Boolean): EntityId {
    return createEntity { eid ->
      add(eid, OwnedBestia(masterId, playerBestiaId))
      add(eid, Position(70, 71, 72))
      add(eid, Level(4))
      if (dead) add(eid, Dead())
    }
  }

  @Test
  fun `a living bestia keeps where it stood and its level`() {
    val world = testWorld()
    val id = world.playerBestia(dead = false)

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    assertEquals(Vec3L(70, 71, 72), row.position)
    assertEquals(4, row.level)
  }

  @Test
  fun `a dead bestia is stored at its save point`() {
    val world = testWorld()
    val id = world.playerBestia(dead = true)

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    assertEquals(savePoint, row.position)
  }

  @Test
  fun `a deleted bestia is skipped`() {
    every { repository.findByIdForUpdate(playerBestiaId) } returns null
    val world = testWorld()
    val id = world.playerBestia(dead = false)

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `a bestia's writes are keyed by its master`() {
    val world = testWorld()
    val id = world.playerBestia(dead = false)

    assertEquals(masterId, sut.snapshot(world, id)?.writeKey)
  }

  @Test
  fun `only player bestias are supported`() {
    val world = testWorld()
    val bestia = world.playerBestia(dead = false)
    val other = world.createEntity { eid -> add(eid, Position(1, 2, 3)) }

    assertTrue(sut.supports(world, bestia))
    assertFalse(sut.supports(world, other))
  }
}
