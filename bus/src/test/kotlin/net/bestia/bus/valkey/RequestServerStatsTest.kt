package net.bestia.bus.valkey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RequestServerStatsTest {

  private val stats = RequestServerStats()

  @Test
  fun `a question is timed under its own name`() {
    stats.of("MapTile").record(2_000)
    stats.of("MapTile").record(3_000)
    stats.of("Catalog").record(500)

    assertEquals(2L, stats.of("MapTile").count)
    assertEquals(5_000L, stats.of("MapTile").totalNanos)
    assertEquals(1L, stats.of("Catalog").count)
  }

  @Test
  fun `asking for a question again gives the same stats`() {
    assertSame(stats.of("MapTile"), stats.of("MapTile"))
  }

  @Test
  fun `a question nobody asked reads zero`() {
    assertEquals(0L, stats.of("Inventory").count)
    assertEquals(0L, stats.of("Inventory").totalNanos)
  }
}
