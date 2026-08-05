package net.bestia.zone.world.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.prop.StaticVisual
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntityFactory
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Instant

/** What a promoted prop's death records about the durable object it was. */
class PropDeathDivergenceSystemTest {

  private fun worldService(): WorldService = mockk {
    every { record } returns mockk { every { pipelineVersion } returns 42L }
  }

  private fun system(divergence: WorldObjectDivergenceRegistry) = PropDeathDivergenceSystem(
    kindRegistry(), mockk<LootItemEntityFactory>(), divergence, worldService()
  )

  private fun kindRegistry() = PropKindRegistry().also { it.load() }

  @Test
  fun `a felled tree is recorded with a future resumeAt at its lattice version`() {
    val divergence: WorldObjectDivergenceRegistry = mockk(relaxed = true)
    val world = testWorld()
    val propId = 123L

    world.createEntity { eid ->
      add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
      add(eid, StaticVisual(StaticEntityKind.TREE, variant = 0, heightDm = 90))
      add(eid, WorldObjectIdentity(propId, latticeVersion = 1L))
      add(eid, Dead)
    }

    system(divergence).update(world, 0f)

    verify {
      divergence.recordDepletion(
        propId, StaticEntityKind.TREE, 42L,
        match<Instant> { it.isAfter(Instant.now()) }
      )
    }
  }

  @Test
  fun `a claimed landmark is recorded as terminal`() {
    val divergence: WorldObjectDivergenceRegistry = mockk(relaxed = true)
    val world = testWorld()
    val propId = 456L

    world.createEntity { eid ->
      add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
      add(eid, StaticVisual(StaticEntityKind.POI_LOST_GRAVE, variant = 0, heightDm = 12))
      add(eid, WorldObjectIdentity(propId, latticeVersion = 1L))
      add(eid, Dead)
    }

    system(divergence).update(world, 0f)

    verify { divergence.recordDepletion(propId, StaticEntityKind.POI_LOST_GRAVE, 42L, null) }
  }

  @Test
  fun `a dead entity with no WorldObjectIdentity is ignored`() {
    val divergence: WorldObjectDivergenceRegistry = mockk(relaxed = true)
    val world = testWorld()

    world.createEntity { eid -> add(eid, Dead) }

    system(divergence).update(world, 0f)

    verify(exactly = 0) { divergence.recordDepletion(any(), any(), any(), any()) }
  }
}
