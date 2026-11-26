package net.bestia.zone.worldevent.rift

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ground.fire.GroundFireService
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.Test

/** A town covers x below 1000; the wave stands in it. */
class WaveFiresTest {

  private val world = testWorld()
  private val groundFire = mockk<GroundFireService> { every { ignite(any(), any(), any(), any(), any()) } returns 1L }
  private val sites = mockk<SettlementSiteIndex> {
    every { siteCovering(any(), any()) } answers { if (firstArg<Long>() < 1_000) mockk() else null }
  }
  private val sut = WaveFires(groundFire, sites)

  private val inTown = Vec3L(500, 500, 10)
  private val wolf = world.createEntity { id -> add(id, Position.fromVec3(inTown)) }

  @Test
  fun `a wave in a town nobody guards lights one fire`() {
    sut.tend(world, riftId = 9, listOf(wolf))
    sut.tend(world, riftId = 9, listOf(wolf))

    verify(exactly = 1) { groundFire.ignite(inTown, 2, wolf, 0, 1) }
  }

  @Test
  fun `the next wave may light another`() {
    sut.tend(world, riftId = 9, listOf(wolf))
    sut.newWave(9)
    sut.tend(world, riftId = 9, listOf(wolf))

    verify(exactly = 2) { groundFire.ignite(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a master in the streets keeps the fields from burning`() {
    world.createEntity { id -> add(id, Master(masterId = 1, name = "Ada")); add(id, Position.fromVec3(Vec3L(550, 500, 10))) }

    sut.tend(world, riftId = 9, listOf(wolf))

    verify(exactly = 0) { groundFire.ignite(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a wave still on the road burns nothing`() {
    val onTheRoad = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(2_000, 500, 10))) }

    sut.tend(world, riftId = 9, listOf(onTheRoad))

    verify(exactly = 0) { groundFire.ignite(any(), any(), any(), any(), any()) }
  }
}
