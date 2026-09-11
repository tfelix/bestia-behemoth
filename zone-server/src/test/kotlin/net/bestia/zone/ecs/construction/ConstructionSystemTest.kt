package net.bestia.zone.ecs.construction

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.PlayerStructureRegistry
import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.world.prop.PropKindDto
import net.bestia.zone.world.prop.PropKindRegistry
import net.bestia.zone.world.prop.StaticEntityKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A site only advances while somebody is standing at it, which is the whole difference between this and the
 * cast and craft timers it is otherwise shaped like.
 */
class ConstructionSystemTest {

  private val siteAt = Vec3L(100, 100, 64)

  private val finalHp = 150

  private lateinit var world: World
  private lateinit var structures: PlayerStructureService
  private lateinit var registry: PlayerStructureRegistry
  private lateinit var system: ConstructionSystem

  @BeforeEach
  fun setUp() {
    world = testWorld()

    structures = mockk(relaxed = true)
    registry = mockk(relaxed = true)

    val propKinds = mockk<PropKindRegistry>()
    every { propKinds.of(any()) } returns PropKindDto(kind = StaticEntityKind.WORKBENCH, maxHp = finalHp)

    system = ConstructionSystem(structures, registry, propKinds)
  }

  @Test
  fun `a site with nobody on it does not advance`() {
    val site = spawnSite(totalSeconds = 10f)

    system.update(world, 1f)

    assertEquals(10f, world.getOrThrow<ConstructionSite>(site).remainingSeconds)
    assertFalse(world.getOrThrow<ConstructionSite>(site).active, "nobody is working")
  }

  @Test
  fun `a builder in range advances it`() {
    val site = spawnSite(totalSeconds = 10f)
    spawnWorker(site, at = siteAt)

    system.update(world, 1f)

    assertEquals(9f, world.getOrThrow<ConstructionSite>(site).remainingSeconds)
    assertTrue(world.getOrThrow<ConstructionSite>(site).active)
  }

  @Test
  fun `two builders advance it twice as fast`() {
    val site = spawnSite(totalSeconds = 10f)
    spawnWorker(site, at = siteAt)
    spawnWorker(site, at = siteAt)

    system.update(world, 1f)

    assertEquals(8f, world.getOrThrow<ConstructionSite>(site).remainingSeconds)
  }

  @Test
  fun `walking out of range stops the work and keeps the progress`() {
    val site = spawnSite(totalSeconds = 10f)
    val worker = spawnWorker(site, at = siteAt)

    system.update(world, 1f)
    world.getOrThrow<Position>(worker).x = siteAt.x + 20
    system.update(world, 1f)

    assertNull(world.get(worker, Building::class), "the builder gave up")
    assertEquals(9f, world.getOrThrow<ConstructionSite>(site).remainingSeconds, "progress is kept")
    assertFalse(world.getOrThrow<ConstructionSite>(site).active)
  }

  @Test
  fun `a dead builder stops working`() {
    val site = spawnSite(totalSeconds = 10f)
    val worker = spawnWorker(site, at = siteAt)
    world.add(worker, Dead())

    system.update(world, 1f)

    assertNull(world.get(worker, Building::class))
    assertEquals(10f, world.getOrThrow<ConstructionSite>(site).remainingSeconds)
  }

  @Test
  fun `a builder whose site is gone stops working`() {
    val site = spawnSite(totalSeconds = 10f)
    val worker = spawnWorker(site, at = siteAt)
    world.destroy(site)

    system.update(world, 1f)

    assertNull(world.get(worker, Building::class))
  }

  @Test
  fun `health rises with the work`() {
    val site = spawnSite(totalSeconds = 10f)
    spawnWorker(site, at = siteAt)

    assertEquals(ConstructionSite.START_HP, world.getOrThrow<Health>(site).max)

    system.update(world, 5f)
    val half = world.getOrThrow<Health>(site)
    assertEquals(76, half.max, "halfway between 1 and $finalHp")
    assertEquals(76, half.current, "an untouched site is at full health")

    system.update(world, 5f)
    assertEquals(finalHp, world.getOrThrow<Health>(site).max)
  }

  @Test
  fun `damage already taken survives the ramp`() {
    val site = spawnSite(totalSeconds = 10f)
    spawnWorker(site, at = siteAt)

    system.update(world, 5f)
    world.getOrThrow<Health>(site).current -= 30

    system.update(world, 2.5f)

    val health = world.getOrThrow<Health>(site)
    assertEquals(113, health.max)
    assertEquals(113 - 30, health.current, "the wound is carried across rather than healed")
  }

  @Test
  fun `it completes when the work is done`() {
    val site = spawnSite(totalSeconds = 2f)
    spawnWorker(site, at = siteAt)
    justRun { structures.completeConstruction(any(), any(), any()) }

    system.update(world, 2f)

    verify(exactly = 1) { structures.completeConstruction(world, site, any()) }
  }

  @Test
  fun `progress is written to the row only once the interval is up`() {
    val site = spawnSite(totalSeconds = 100f)
    spawnWorker(site, at = siteAt)

    system.update(world, 9f)
    verify(exactly = 0) { registry.updateProgress(any(), any()) }

    system.update(world, 1f)
    verify(exactly = 1) { registry.updateProgress(STRUCTURE_ID, 90f) }
  }

  private fun spawnSite(totalSeconds: Float): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(siteAt))
      add(id, Health(ConstructionSite.START_HP, ConstructionSite.START_HP))
      add(
        id,
        ConstructionSite(
          kind = StaticEntityKind.WORKBENCH,
          ownerMasterId = 7L,
          structureId = STRUCTURE_ID,
          yaw = 0f,
          totalSeconds = totalSeconds
        )
      )
    }
  }

  private fun spawnWorker(site: EntityId, at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Building(site))
    }
  }

  private companion object {
    const val STRUCTURE_ID = 42L
  }
}
