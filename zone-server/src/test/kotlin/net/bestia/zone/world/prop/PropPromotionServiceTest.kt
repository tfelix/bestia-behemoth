package net.bestia.zone.world.prop

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.PropPose
import net.bestia.zone.ecs.prop.PropVitality
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Promotion: turning a static prop into an attackable entity the first time something targets or damages it.
 */
class PropPromotionServiceTest {

  private fun noDivergence(): WorldObjectDivergenceRegistry = mockk { every { of(any()) } returns null }

  private fun propEntity(world: World, propId: Long = 1L, maxHp: Int = 50): EntityId =
    world.createEntity { id ->
      add(id, PropPose(Vec3L(1, 2, 3), 0f))
      add(id, PropVitality(maxHp))
      add(id, WorldObjectIdentity(propId, latticeVersion = 1L))
      add(id, StaticSync)
    }

  @Test
  fun `promoting a pristine prop adds Position, Health and StatusValues`() {
    val world = testWorld()
    val service = PropPromotionService(noDivergence())
    val id = propEntity(world)

    assertTrue(service.promoteIfNeeded(world, id))

    assertEquals(Vec3L(1, 2, 3), world.get(id, Position::class)?.toVec3L())
    assertEquals(50, world.get(id, Health::class)?.current)
    assertEquals(50, world.get(id, Health::class)?.max)
    assertTrue(world.get(id, StatusValues::class) != null, "a promoted prop needs StatusValues to fight at all")
  }

  @Test
  fun `promoting an already-promoted prop leaves its Health alone`() {
    val world = testWorld()
    val service = PropPromotionService(noDivergence())
    val id = propEntity(world)

    service.promoteIfNeeded(world, id)
    world.get(id, Health::class)!!.current = 10 // simulate a hit already landed

    assertTrue(service.promoteIfNeeded(world, id), "an already-promoted prop must still resolve")
    assertEquals(10, world.get(id, Health::class)?.current, "a second promotion must not reset accumulated damage")
  }

  @Test
  fun `an entity with no WorldObjectIdentity is treated as already resolvable, not promoted`() {
    val world = testWorld()
    val service = PropPromotionService(noDivergence())
    val id = world.createEntity { }

    assertTrue(service.promoteIfNeeded(world, id), "an ordinary entity is not this service's concern")
    assertFalse(world.has(id, Position::class), "nothing should have been added to a non-prop entity")
  }

  @Test
  fun `a terminally diverged prop refuses promotion`() {
    val world = testWorld()
    val divergence: WorldObjectDivergenceRegistry = mockk {
      every { of(7L) } returns DivergenceEntry(StaticEntityKind.POI_LOST_GRAVE, DivergenceState.DEPLETED, null)
    }
    val service = PropPromotionService(divergence)
    val id = propEntity(world, propId = 7L)

    assertFalse(service.promoteIfNeeded(world, id), "a claimed landmark must not stand back up")
    assertFalse(world.has(id, Position::class))
  }
}
