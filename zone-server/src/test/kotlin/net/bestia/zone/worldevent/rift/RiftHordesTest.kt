package net.bestia.zone.worldevent.rift

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.spawn.ecs.DenIdentity
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiftHordesTest {

  private val world = testWorld()
  private val wolf = Bestia(id = 1, identifier = "wolf", levelMin = 20, levelMax = 22, experienceReward = 1, health = 10, mana = 1)
  private val warden = Bestia(id = 116, identifier = "rift_warden", levelMin = 60, levelMax = 60, experienceReward = 1, health = 10, mana = 1)
    .apply { boss = true; eventOnly = true }

  private val provinces = mockk<ProvinceService> { every { provinceAt(any(), any())?.index } returns 0 }
  private val dens = mockk<WildSpawnerService> {
    every { dens } returns listOf(WildSpawnerService.Den(DenIdentity(1, 1, 1), wolf.id, Vec3L(100, 100, 0), 3, 10, 60))
  }
  private val catalogue = mockk<BestiaCatalogue> {
    every { find(wolf.id) } returns wolf
    every { byIdentifier("rift_warden") } returns warden
  }
  private val spawner = mockk<BestiaEntitySpawner> {
    every { spawnMob(any(), any<Long>(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      world.createEntity { }
    }
  }
  private val chunks = mockk<ChunkService> { every { surfaceElevationAt(any(), any()) } returns 40.0 }
  private val worldService = mockk<WorldService>(relaxed = true) {
    every { config } returns WorldConfig(seed = 1, widthCells = 128, heightCells = 128)
  }
  private val clock = mockk<BestiaClock> { every { speedFactor } returns 3.0 }

  private val sut = RiftHordes(RiftConfig(), provinces, dens, catalogue, spawner, chunks, worldService, mockk(relaxed = true), clock, mockk(relaxed = true))

  private fun openRift(stage: Int) = StoredRift(x = 5_000.0, y = 5_000.0, phase = Phase.OPEN, stage = stage).also { it.id = 9 }

  @Test
  fun `an unwatched gate sends a small wave and schedules the next`() {
    val rift = openRift(stage = 1)

    sut.tend(world, listOf(rift), now = 1_000)

    verify(exactly = 2) { spawner.spawnMob(any(), wolf.id, any(), any(), any(), false, any(), any(), any(), 22 + 5, any()) }
    assertTrue(rift.nextWaveAtSecond in (1_000 + 20 * 60 * 3)..(1_000 + 30 * 60 * 3))
  }

  @Test
  fun `a watched gate sends a full wave`() {
    world.createEntity { id -> add(id, Master(masterId = 1, name = "Ada")); add(id, Position.fromVec3(Vec3L(5_100, 5_000, 0))) }

    sut.tend(world, listOf(openRift(stage = 2)), now = 0)

    verify(exactly = 5) { spawner.spawnMob(any(), wolf.id, any(), any(), any(), false, any(), any(), any(), 22 + 10, any()) }
  }

  @Test
  fun `the anchor steps out at stage three and its fall is remembered`() {
    val rift = openRift(stage = 2).apply { nextWaveAtSecond = Long.MAX_VALUE }
    sut.tend(world, listOf(rift), now = 0)
    assertNull(rift.anchorEntityId)

    rift.stage = 3
    sut.tend(world, listOf(rift), now = 0)
    val anchor = rift.anchorEntityId!!
    verify { spawner.spawnMob(any(), warden.id, any(), any(), any(), any(), any(), any(), any(), 22 + 10, any()) }

    world.destroy(anchor)
    sut.tend(world, listOf(rift), now = 0)
    assertTrue(rift.anchorSlain)
    assertNull(rift.anchorEntityId)
  }

  @Test
  fun `a rift that ended takes its creatures with it`() {
    val rift = openRift(stage = 3)
    sut.tend(world, listOf(rift), now = 0)
    val anchor = rift.anchorEntityId!!

    rift.phase = Phase.COLLAPSED
    sut.tend(world, listOf(rift), now = 0)

    assertFalse(world.isAlive(anchor))
    assertEquals(null, rift.anchorEntityId)
  }
}
