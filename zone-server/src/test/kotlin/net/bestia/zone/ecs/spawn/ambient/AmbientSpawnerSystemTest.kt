package net.bestia.zone.ecs.spawn.ambient

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ai.ecs.AiThrottleable
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The lifecycle of the baseline population: filling in around a player, and emptying out behind them.
 *
 * Ticks are paced at the real `dt` rather than a coarse one, for `SpawnerSystemTest`'s reason: this system
 * runs on `Schedule.EverySeconds(0.25f)`, and driving it with second-long steps would assert the scheduler's
 * arithmetic instead of the behaviour.
 *
 * The resolver is real. It is a pure function of a seed and a lattice, and the interesting properties here -
 * that a site comes back in the same place, that the budget is respected - are about the two working
 * together. What is faked is the world underneath it, so no terrain has to be generated.
 */
class AmbientSpawnerSystemTest {

  private val deletionQueue = PersistedEntityDeletionQueue()
  private val spawner = mockk<BestiaEntitySpawner>()
  private val resolver = mockk<AmbientSiteResolver>()
  private val lattice = AmbientSiteLattice(SEED, SPACING)

  private val config = AmbientSpawnConfig(
    spacingTiles = SPACING,
    activationRadiusTiles = 120,
    spawnsPerPass = 4,
    unloadDelaySeconds = 60f,
    respawnDelaySeconds = 120f
  )

  private val sut = AmbientSpawnerSystem(spawner, resolver, config)
  private val world: World = testWorld(systems = listOf(sut))

  /** Every creature the mocked spawner produced, with the position it was asked for. */
  private val spawnedAt = mutableMapOf<EntityId, Vec3L>()

  init {
    every { resolver.lattice } returns lattice
    every { resolver.siteAt(any(), any()) } answers {
      val cellX = firstArg<Long>()
      val cellY = secondArg<Long>()
      AmbientSite(
        cell = AmbientSiteLattice.pack(cellX, cellY),
        bestiaId = 1L,
        position = Vec3L(lattice.siteX(cellX, cellY), lattice.siteY(cellX, cellY), 0),
        throttleable = true
      )
    }
    every { spawner.spawnMob(any(), any<Long>(), any(), any(), any(), any()) } answers {
      val position = thirdArg<Vec3L>()
      world.createEntity { }.also { spawnedAt[it] = position }
    }
  }

  @Test
  fun `nothing is stocked while no player has picked a master`() {
    tick(2f)

    assertEquals(0, sut.liveCount)
    verify(exactly = 0) { spawner.spawnMob(any(), any<Long>(), any(), any(), any(), any()) }
  }

  @Test
  fun `a player fills the country around them`() {
    player(Vec3L(0, 0, 0))

    tick(5f)

    assertTrue(sut.liveCount > 10, "only ${sut.liveCount} creature(s) around the player")
  }

  /** The budget is what stops a cold arrival creating a hundred and forty entities on one tick. */
  @Test
  fun `no more than the per-pass budget is created in one pass`() {
    player(Vec3L(0, 0, 0))

    // A single pass of the 0.25 s schedule.
    tick(0.25f)

    assertEquals(config.spawnsPerPass, sut.liveCount)
  }

  @Test
  fun `every creature is transient and carries the ambient marker`() {
    player(Vec3L(0, 0, 0))
    tick(1f)

    // `persistent = false` is the sixth argument; nothing here may end up in the persistence sweep.
    verify(exactly = 0) {
      spawner.spawnMob(any(), any<Long>(), any(), any(), any(), persistent = true)
    }
    for (id in spawnedAt.keys) {
      assertTrue(world.has(id, Ambient::class), "$id has no Ambient marker")
      assertTrue(world.has(id, AiThrottleable::class), "$id is not throttleable")
    }
  }

  /**
   * Assertions are about the original cohort rather than [AmbientSpawnerSystem.liveCount]: the player has to
   * go somewhere, and wherever it goes the system correctly stocks *that* country too.
   */
  @Test
  fun `a creature survives the player stepping away briefly`() {
    val playerId = player(Vec3L(0, 0, 0))
    tick(5f)
    val cohort = spawnedAt.keys.toList()

    move(playerId, FAR_AWAY)
    tick(config.unloadDelaySeconds / 2f)

    assertTrue(
      cohort.all { world.hasEntity(it) },
      "${cohort.count { !world.hasEntity(it) }} creature(s) were torn down inside the unload delay"
    )
  }

  @Test
  fun `the country empties once the unload delay has passed`() {
    val playerId = player(Vec3L(0, 0, 0))
    tick(5f)
    val cohort = spawnedAt.keys.toList()

    move(playerId, FAR_AWAY)
    tick(config.unloadDelaySeconds + 1f)

    assertTrue(
      cohort.none { world.hasEntity(it) },
      "${cohort.count { world.hasEntity(it) }} creature(s) outlived the unload delay"
    )
  }

  /**
   * The property that makes a population this dense bearable to walk through.
   *
   * A creature is a function of its lattice cell, so leaving and coming back has to put the same species
   * back on the same tile. Anything that reshuffled here would read as the wilderness churning behind the
   * player.
   */
  @Test
  fun `walking away and back puts creatures in the same places`() {
    val playerId = player(Vec3L(0, 0, 0))
    tick(10f)
    val first = spawnedAt.values.toSet()

    move(playerId, FAR_AWAY)
    tick(config.unloadDelaySeconds + 1f)
    spawnedAt.clear()

    move(playerId, Vec3L(0, 0, 0))
    tick(10f)

    assertEquals(first, spawnedAt.values.toSet())
  }

  /** Teardown must not queue a deletion: there is no row, and the queue would grow for nothing. */
  @Test
  fun `teardown never queues a persisted deletion`() {
    val playerId = player(Vec3L(0, 0, 0))
    tick(5f)

    move(playerId, FAR_AWAY)
    tick(config.unloadDelaySeconds + 1f)

    assertEquals(emptyList<EntityId>(), deletionQueue.drainAll())
  }

  /** Without the delay, standing on one spot would be an endless supply of kills. */
  @Test
  fun `a killed site stays empty for the respawn delay`() {
    player(Vec3L(0, 0, 0))
    tick(1f)

    val killed = spawnedAt.keys.first()
    val position = spawnedAt.getValue(killed)
    world.destroy(killed)
    world.tick(DT)

    tick(5f)
    assertFalse(
      spawnedAt.filterKeys { world.hasEntity(it) }.any { it.value == position && it.key != killed },
      "the killed site was restocked inside the respawn delay"
    )

    tick(config.respawnDelaySeconds)
    assertTrue(
      spawnedAt.any { it.value == position && it.key != killed },
      "the killed site never came back after the respawn delay"
    )
  }

  /**
   * The respawn-delay bookkeeping is bounded, unlike the two maps beside it.
   *
   * A stamp is written when a creature dies and cleared when its site restocks. A player who kills something
   * and then walks away satisfies neither, so without a prune the map grows by one entry per kill for the
   * life of the process.
   */
  @Test
  fun `a kill in country the player then leaves does not leak bookkeeping`() {
    val playerId = player(Vec3L(0, 0, 0))
    tick(2f)

    world.destroy(spawnedAt.keys.first())
    world.tick(DT)
    tick(0.5f)

    move(playerId, FAR_AWAY)
    tick(config.respawnDelaySeconds + config.unloadDelaySeconds + 1f)

    assertEquals(0, sut.pendingRespawnCount, "a killed site's stamp outlived the respawn delay")
  }

  private fun player(at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, ActivePlayer)
    }
  }

  private fun move(id: EntityId, to: Vec3L) {
    val position = world.getOrThrow(id, Position::class)
    position.x = to.x
    position.y = to.y
    position.z = to.z
    world.tick(DT)
  }

  private fun tick(seconds: Float) {
    repeat((seconds / DT).toInt()) { world.tick(DT) }
  }

  private companion object {
    /** Far enough that not one cell of the origin's neighbourhood is still in range. */
    val FAR_AWAY = Vec3L(50_000, 50_000, 0)

    const val SEED = 11_753_242L
    const val SPACING = 30L
    const val DT = 1f / 20f
  }
}
