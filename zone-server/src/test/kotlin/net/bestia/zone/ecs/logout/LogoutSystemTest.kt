package net.bestia.zone.ecs.logout

import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.persistence.PersistAndRemove
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogoutSystemTest {

  @Test
  fun `a protected body leaves once the protection runs out`() {
    val world = testWorld(systems = listOf(LogoutSystem(mockk(relaxed = true), mockk(relaxed = true))))
    val body = world.createEntity { }
    world.add(body, DisconnectProtection(remainingSeconds = 1f))

    world.tick(1.5f)

    assertTrue(world.has(body, PersistAndRemove::class))
    assertFalse(world.has(body, DisconnectProtection::class))
  }
}
