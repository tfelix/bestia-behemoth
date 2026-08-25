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
import net.bestia.zone.item.loot.LootItemEntitySpawner
import org.junit.jupiter.api.Test
import java.time.Instant

/** What a promoted prop's death records about the durable object it was. */
class PropDeathDivergenceSystemTest {

  private fun system(divergence: WorldObjectDivergenceRegistry) = PropDeathDivergenceSystem(
    kindRegistry(), mockk<LootItemEntitySpawner>(), divergence
  )

  private fun kindRegistry() = PropKindRegistry().also { it.load() }

  /**
   * A prop nothing has claimed yet - the state every test but the collect one below starts from.
   *
   * Stated rather than left to `relaxed = true`, which answers a nullable getter with a stub object rather
   * than with null. That would trip the "already depleted" guard and make these tests pass or fail for a
   * reason that has nothing to do with what they are checking.
   */
  private fun pristine(): WorldObjectDivergenceRegistry = mockk(relaxed = true) {
    every { of(any()) } returns null
  }

  @Test
  fun `a felled tree is recorded with a future resumeAt`() {
    val divergence = pristine()
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
        propId, StaticEntityKind.TREE,
        match<Instant> { it.isAfter(Instant.now()) }
      )
    }
  }

  @Test
  fun `a claimed landmark is recorded as terminal`() {
    val divergence = pristine()
    val world = testWorld()
    val propId = 456L

    world.createEntity { eid ->
      add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
      add(eid, StaticVisual(StaticEntityKind.POI_LOST_GRAVE, variant = 0, heightDm = 12))
      add(eid, WorldObjectIdentity(propId, latticeVersion = 1L))
      add(eid, Dead)
    }

    system(divergence).update(world, 0f)

    verify { divergence.recordDepletion(propId, StaticEntityKind.POI_LOST_GRAVE, null) }
  }

  /**
   * A prop now has two ways to be used up, and they must not both pay out. If a crystal is collected at order
   * 64 and an attacker's in-flight damage finishes it at order 65 in the same tick, only the collect counts.
   */
  @Test
  fun `a prop already depleted by a collect is not paid out again on death`() {
    val divergence: WorldObjectDivergenceRegistry = mockk(relaxed = true) {
      every { of(any()) } returns
        DivergenceEntry(StaticEntityKind.MANA_CRYSTAL_SMALL, DivergenceState.DEPLETED, null)
    }
    val world = testWorld()

    world.createEntity { eid ->
      add(eid, Position.fromVec3(Vec3L(1, 2, 3)))
      add(eid, StaticVisual(StaticEntityKind.MANA_CRYSTAL_SMALL, variant = 0, heightDm = 12))
      add(eid, WorldObjectIdentity(789L, latticeVersion = 1L))
      add(eid, Dead)
    }

    system(divergence).update(world, 0f)

    verify(exactly = 0) { divergence.recordDepletion(any(), any(), any()) }
  }

  @Test
  fun `a dead entity with no WorldObjectIdentity is ignored`() {
    val divergence = pristine()
    val world = testWorld()

    world.createEntity { eid -> add(eid, Dead) }

    system(divergence).update(world, 0f)

    verify(exactly = 0) { divergence.recordDepletion(any(), any(), any()) }
  }
}
