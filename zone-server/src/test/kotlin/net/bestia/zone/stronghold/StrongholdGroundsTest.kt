package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrongholdGroundsTest {

  private val world = testWorld()
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val held = mutableListOf<Stronghold>()

  private val grounds = StrongholdGrounds(
    strongholds = mockk { every { heldBy(any()) } answers { held.filter { it.guildId == firstArg<Long>() } } },
    seeds = mockk { every { awake() } returns listOf(seed) },
    worldService = mockk<WorldService> { every { config.voxelSize } returns 1.0 },
    config = StrongholdConfig(groundsRadiusMetres = 500.0),
  )

  @Test
  fun `a member is inside on the grounds of a standing Empyrite of the guild`() {
    held.add(Stronghold(seed.rank, GUILD, State.STANDING, null, Instant.EPOCH, Instant.EPOCH))

    assertTrue(grounds.isInOwnStronghold(world, entityAt(1_400), GUILD))
    assertFalse(grounds.isInOwnStronghold(world, entityAt(1_600), GUILD))
    assertFalse(grounds.isInOwnStronghold(world, entityAt(1_400), OTHER_GUILD))
  }

  @Test
  fun `a site is not a Stronghold yet`() {
    held.add(Stronghold(seed.rank, GUILD, State.SITE, 40L, Instant.EPOCH, Instant.EPOCH))

    assertFalse(grounds.isInOwnStronghold(world, entityAt(1_000), GUILD))
  }

  private fun entityAt(east: Long) = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(east, 1_000, 0))) }

  private companion object {
    const val GUILD = 1L
    const val OTHER_GUILD = 2L
  }
}
