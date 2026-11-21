package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.ClosedToAttack
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.stronghold.ecs.AttackWindowSystem
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttackWindowSystemTest {

  private val world = testWorld()
  private val seed = EmpyriteSeed(rank = 2, x = 1_000.0, y = 1_000.0, name = "Hochvein", settlement = -1)
  private val stronghold = Stronghold(seed.rank, 1L, State.STANDING, null, Instant.EPOCH, Instant.EPOCH)

  private fun systemAt(now: String) = AttackWindowSystem(
    strongholds = mockk { every { on(any()) } answers { stronghold.takeIf { firstArg<Int>() == it.rank } } },
    seeds = mockk { every { awake() } returns listOf(seed) },
    worldService = mockk<WorldService> { every { config.voxelSize } returns 1.0 },
    config = StrongholdConfig(attackWindows = listOf("SATURDAY 18:00-22:00")),
    clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
  )

  @Test
  fun `outside a window guild structures on the grounds close, and a banner far off stays open`() {
    val empyrite = guildOwnedAt(1_000)
    val farBanner = guildOwnedAt(3_000)

    systemAt("2026-11-21T12:00:00Z").update(world, 10f)

    assertTrue(world.has(empyrite, ClosedToAttack::class))
    assertFalse(world.has(farBanner, ClosedToAttack::class))
  }

  @Test
  fun `when a window opens they open again`() {
    val empyrite = guildOwnedAt(1_000)
    systemAt("2026-11-21T12:00:00Z").update(world, 10f)

    systemAt("2026-11-21T19:00:00Z").update(world, 10f)

    assertFalse(world.has(empyrite, ClosedToAttack::class))
  }

  private fun guildOwnedAt(east: Long) = world.createEntity { id ->
    add(id, GuildOwned(1L))
    add(id, Position.fromVec3(Vec3L(east, 1_000, 0)))
  }
}
