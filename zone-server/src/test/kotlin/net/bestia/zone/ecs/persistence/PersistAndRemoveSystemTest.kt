package net.bestia.zone.ecs.persistence

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class PersistAndRemoveSystemTest {

  private val writeBehind = mockk<EntityWriteBehind>(relaxed = true)
  private val world = testWorld(systems = listOf(PersistAndRemoveSystem(writeBehind)))

  @Test
  fun `a leaving entity is snapshotted for the write-behind before it is destroyed`() {
    val leaving = world.createEntity { id -> add(id, PersistAndRemove) }

    world.tick(0.05f)

    verify(exactly = 1) { writeBehind.persist(any(), listOf(leaving), any()) }
    assertFalse(world.isAlive(leaving))
  }
}
