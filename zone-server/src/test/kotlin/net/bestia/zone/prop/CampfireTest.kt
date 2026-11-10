package net.bestia.zone.prop

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class CampfireTest {

  private val now = Instant.parse("2026-11-10T12:00:00Z")

  @Test
  fun `a log adds half an hour to a burning fire`() {
    assertEquals(now + Duration.ofMinutes(40), Campfire.withLog(now + Duration.ofMinutes(10), now))
  }

  @Test
  fun `a log lights a cold fire from now`() {
    assertEquals(now + Duration.ofMinutes(30), Campfire.withLog(now - Duration.ofMinutes(10), now))
  }

  @Test
  fun `a fire holds two hours at most`() {
    assertEquals(now + Duration.ofHours(2), Campfire.withLog(now + Duration.ofMinutes(100), now))
    assertNull(Campfire.withLog(now + Duration.ofHours(2), now), "a full fire takes no more wood")
  }

  @Test
  fun `a fire is cold once it burnt out and gone half an hour later`() {
    val fire = campfire(burnsOutAt = now)

    assertTrue(fire.canWorkAt(now - Duration.ofSeconds(1)))
    assertFalse(fire.canWorkAt(now))
    assertFalse(fire.isGoneAt(now + Duration.ofMinutes(29)))
    assertTrue(fire.isGoneAt(now + Duration.ofMinutes(30)))
  }

  @Test
  fun `a station that burns nothing always works and is never gone`() {
    val bench = StructureEntry(1L, StaticEntityKind.WORKBENCH, 1L, 1L, Vec3L(0, 0, 0), yaw = 0f)

    assertTrue(bench.canWorkAt(now))
    assertFalse(bench.isGoneAt(now + Duration.ofDays(365)))
  }

  private fun campfire(burnsOutAt: Instant): StructureEntry {
    return StructureEntry(1L, StaticEntityKind.CAMPFIRE, 1L, 1L, Vec3L(0, 0, 0), yaw = 0f, burnsOutAt = burnsOutAt)
  }
}
