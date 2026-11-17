package net.bestia.zone.poi

import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.Poi
import org.junit.jupiter.api.Test

class PoiDeathSystemTest {

  private val pois = mockk<PoiService>(relaxed = true)
  private val system = PoiDeathSystem(pois)
  private val world = testWorld(systems = listOf(system))

  @Test
  fun `a POI at zero hp is recorded as destroyed, a standing one is not`() {
    val broken = world.createEntity { id ->
      add(id, Poi(PoiKind.WAYSTONE, instanceId = 2))
      add(id, Dead())
    }
    val standing = world.createEntity { id -> add(id, Poi(PoiKind.WAYSTONE, instanceId = 3)) }

    world.tick(0f)

    verify(exactly = 1) { pois.destroyed(any(), broken) }
    verify(exactly = 0) { pois.destroyed(any(), standing) }
  }
}
