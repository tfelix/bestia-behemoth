package net.bestia.zone.architecture

import net.bestia.zone.ecs.core.EcsWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * The order the systems run in, compared to a stored copy.
 *
 * Moving a system to another package or changing which system declares an ordering must not change it. A
 * change that is meant to reorder the tick updates `tick-order.txt` in the same commit.
 */
@SpringBootTest
@ActiveProfiles("no-socket", "test")
class TickOrderSnapshotTest {

  @Autowired
  private lateinit var world: EcsWorld

  @Test
  fun `systems run in the stored order`() {
    val stored = javaClass.getResource("/architecture/tick-order.txt")!!.readText()

    assertEquals(stored.replace("\r\n", "\n").trim(), world.describeSystems().trim())
  }
}
