package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.worldgen.stronghold.EmpyriteSeeds
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.settlement.Town
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.stronghold.persistence.EmpyriteSeedState
import net.bestia.zone.stronghold.persistence.EmpyriteSeedStateRepository
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmpyriteSeedRegistryTest {

  private val towns = mutableListOf<Town>()
  private val saved = mutableListOf<EmpyriteSeedState>()
  private val stored = mutableListOf<EmpyriteSeedState>()

  private val repository = mockk<EmpyriteSeedStateRepository>(relaxed = true) {
    every { findAll() } answers { stored }
    every { save(any()) } answers { firstArg<EmpyriteSeedState>().also { saved.add(it) } }
  }

  private val worldService = mockk<WorldService> {
    every { generated } returns world
    every { config } returns world.config
    every { record.shapeVersion } returns SHAPE_VERSION
    every { record.pipelineVersion } returns PIPELINE_VERSION
  }

  private val registry = EmpyriteSeedRegistry(
    worldService = worldService,
    towns = mockk<TownRegistry> { every { all() } answers { towns } },
    config = StrongholdConfig(),
    repository = repository,
    // Runs the durable write on the calling thread, so a test can assert on it without waiting.
    asyncJobExecutor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    },
  )

  @Test
  fun `the best seeds of the pool wake first`() {
    registry.restoreAll()

    registry.wakeUpTo(2)

    assertEquals(pool.take(2), registry.awake())
    assertEquals(listOf(0, 1), saved.filter { it.awake }.map { it.rank })
  }

  @Test
  fun `an awake seed never goes back to sleep`() {
    registry.restoreAll()
    registry.wakeUpTo(2)

    registry.wakeUpTo(1)

    assertEquals(2, registry.awake().size)
  }

  @Test
  fun `a seed by a player town is skipped for good and the next one wakes`() {
    towns.add(townAt(pool[0], metresAway = 900.0))
    registry.restoreAll()

    registry.wakeUpTo(1)
    towns.clear()
    registry.wakeUpTo(2)

    assertEquals(listOf(pool[1], pool[2]), registry.awake())
    assertFalse(saved.first { it.rank == 0 }.awake)
  }

  @Test
  fun `a town further than the clearance does not keep a seed asleep`() {
    towns.add(townAt(pool[0], metresAway = 1_100.0))
    registry.restoreAll()

    registry.wakeUpTo(1)

    assertEquals(listOf(pool[0]), registry.awake())
  }

  @Test
  fun `seeds woken on another world are dropped at boot`() {
    stored.add(EmpyriteSeedState(0, true, Instant.EPOCH, SHAPE_VERSION, PIPELINE_VERSION + 1))
    stored.add(EmpyriteSeedState(1, true, Instant.EPOCH, SHAPE_VERSION, PIPELINE_VERSION))

    registry.restoreAll()

    assertEquals(listOf(pool[1]), registry.awake())
    verify { repository.deleteAll(listOf(stored[0])) }
  }

  @Test
  fun `a new town keeps its distance from an awake seed`() {
    registry.restoreAll()
    registry.wakeUpTo(1)
    val seed = pool[0]

    assertTrue(registry.isAwakeSeedWithin(voxelAt(seed.x + 900.0, seed.y), 1_000.0))
    assertFalse(registry.isAwakeSeedWithin(voxelAt(seed.x + 1_100.0, seed.y), 1_000.0))
    assertFalse(registry.isAwakeSeedWithin(voxelAt(pool[1].x, pool[1].y), 1_000.0), "a sleeping seed counts")
  }

  private fun townAt(seed: EmpyriteSeed, metresAway: Double): Town {
    return Town(
      id = 1, name = "Oakhold", founderMasterId = 3, founderAccountId = 7, founderName = "Ada",
      sign = voxelAt(seed.x + metresAway, seed.y), state = PlayerSettlement.State.SITE, siteStructureId = 11,
      siteWorkedAt = Instant.EPOCH, signHp = 2000, fellAt = null,
    )
  }

  private fun voxelAt(x: Double, y: Double): Vec3L {
    val voxel = world.config.voxelSize
    return Vec3L((x / voxel).toLong(), (y / voxel).toLong(), 0)
  }

  private companion object {
    const val SHAPE_VERSION = 5L
    const val PIPELINE_VERSION = 9L

    val world: GeneratedWorld
      get() = TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))

    val pool: List<EmpyriteSeed> by lazy {
      EmpyriteSeeds.of(world).also { check(it.size >= 3) { "the test world holds only ${it.size} seeds" } }
    }
  }
}
