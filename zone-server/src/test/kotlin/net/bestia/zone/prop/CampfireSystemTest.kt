package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class CampfireSystemTest {

  private val now = Instant.parse("2026-11-10T12:00:00Z")
  private val world: EcsWorld = testWorld()

  private val structures = mockk<PlayerStructureRegistry>(relaxed = true)
  private val source = mockk<PlayerStructureSource>(relaxed = true)
  private val residency = mockk<WorldObjectResidencyService>(relaxed = true)
  private val system = CampfireSystem(
    structures = structures,
    source = source,
    residency = residency,
    worldService = mockk<WorldService> { every { config.chunkSize } returns 32 },
    clock = Clock.fixed(now, ZoneOffset.UTC)
  )

  @Test
  fun `a fire that went out is swapped for its cold look`() {
    val fire = givenFire(burnsOutAt = now - Duration.ofMinutes(1))
    val shown = givenLoaded(fire, Campfire.LIT_VARIANT)

    system.update(world, 5f)

    verify { residency.remove(world, shown) }
    verify { residency.placeNow(world, source.siteOf(fire)) }
  }

  @Test
  fun `a fire that still burns is left alone`() {
    val fire = givenFire(burnsOutAt = now + Duration.ofMinutes(1))
    givenLoaded(fire, Campfire.LIT_VARIANT)

    system.update(world, 5f)

    verify(exactly = 0) { residency.remove(any(), any()) }
  }

  /** Nobody has to be near: the row goes whether or not the fire is in the world. */
  @Test
  fun `a fire left cold too long is gone`() {
    val fire = givenFire(burnsOutAt = now - Duration.ofMinutes(31))

    system.update(world, 5f)

    verify { structures.remove(fire.id, chunkX = 3, chunkY = 3) }
  }

  private fun givenFire(burnsOutAt: Instant): StructureEntry {
    val fire = StructureEntry(
      5L, StaticEntityKind.CAMPFIRE, 42L, 1L, Vec3L(100, 100, 0), yaw = 0f, burnsOutAt = burnsOutAt
    )
    every { structures.all() } returns listOf(fire)
    return fire
  }

  private fun givenLoaded(fire: StructureEntry, variant: Int): EntityId {
    return world.createEntity { id ->
      add(id, PlayerStructureIdentity(fire.id, 1L))
      add(id, StaticVisual(StaticEntityKind.CAMPFIRE, variant = variant, heightDm = 10))
    }
  }
}
