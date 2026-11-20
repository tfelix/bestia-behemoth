package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.casting.sense.SenseKind
import net.bestia.zone.casting.sense.SenseMarksSMSG
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmpyriteSeedDiscoverySystemTest {

  private val world = testWorld()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val worldService = mockk<WorldService> {
    every { config.voxelSize } returns 1.0
    every { record.shapeVersion } returns 1L
    every { record.pipelineVersion } returns 1L
  }
  private val discoveries = EmpyriteSeedDiscoveries(
    worldService,
    mockk(relaxed = true),
    mockk<AsyncJobExecutor>(relaxed = true),
  )
  private val system = EmpyriteSeedDiscoverySystem(
    seeds = mockk { every { awake() } returns listOf(EmpyriteSeed(2, 1_000.0, 1_000.0, "Hochvein", -1)) },
    discoveries = discoveries,
    worldService = worldService,
    messages = messages,
    config = StrongholdConfig(coreRadiusMetres = 5.0),
  )

  @Test
  fun `a master on the core discovers the seed and is told its name once`() {
    masterAt(Vec3L(1_003, 1_003, 0))

    system.update(world, 1f)
    system.update(world, 1f)

    assertTrue(discoveries.isDiscovered(MASTER_ID, 2))
    verify(exactly = 1) {
      messages.sendToPlayer(ACCOUNT_ID, match<SenseMarksSMSG> {
        it.marks.single().kind == SenseKind.EMPYRITE_SEED_FOUND && it.marks.single().label == "Hochvein"
      })
    }
  }

  @Test
  fun `a master beside the core discovers nothing`() {
    masterAt(Vec3L(1_006, 1_000, 0))

    system.update(world, 1f)

    assertFalse(discoveries.isDiscovered(MASTER_ID, 2))
    verify(exactly = 0) { messages.sendToPlayer(any(), any<SMSG>()) }
  }

  private fun masterAt(at: Vec3L) = world.createEntity { id ->
    add(id, Account(ACCOUNT_ID))
    add(id, Master(MASTER_ID))
    add(id, Position.fromVec3(at))
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 5L
  }
}
