package net.bestia.zone.landmark

import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.Landmark
import org.junit.jupiter.api.Test

class LandmarkDeathSystemTest {

  private val landmarks = mockk<LandmarkService>(relaxed = true)
  private val system = LandmarkDeathSystem(landmarks)
  private val world = testWorld(systems = listOf(system))

  @Test
  fun `a landmark at zero hp is recorded as destroyed, a standing one is not`() {
    val broken = world.createEntity { id ->
      add(id, Landmark(PoiKind.WAYSTONE, copy = 2))
      add(id, Dead())
    }
    val standing = world.createEntity { id -> add(id, Landmark(PoiKind.WAYSTONE, copy = 3)) }

    world.tick(0f)

    verify(exactly = 1) { landmarks.destroyed(any(), broken) }
    verify(exactly = 0) { landmarks.destroyed(any(), standing) }
  }
}
