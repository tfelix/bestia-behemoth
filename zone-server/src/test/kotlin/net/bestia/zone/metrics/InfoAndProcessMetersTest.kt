package net.bestia.zone.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.persistence.PersistedWorld
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InfoAndProcessMetersTest {

  private val meters = SimpleMeterRegistry()

  @Test
  fun `the info gauge carries what the zone is in its tags`() {
    val record = mockk<PersistedWorld> {
      every { pipelineVersion } returns 11L
      every { blockPaletteVersion } returns 5L
      every { chunkFormatVersion } returns 2
      every { shapeVersion } returns 99L
    }
    val worlds = mockk<WorldService> {
      every { isLoaded } returns true
      every { this@mockk.record } returns record
    }

    InfoMeters(meters, worlds, "1.2.3").bind()

    val info = meters.get("zone.info").tag("version", "1.2.3").tag("pipeline", "11").tag("shape", "99").gauge()
    assertEquals(1.0, info.value())
    assertEquals("2", info.id.getTag("chunk_format"))
  }

  @Test
  fun `without a world there is nothing to export and nothing fails`() {
    val worlds = mockk<WorldService> { every { isLoaded } returns false }

    InfoMeters(meters, worlds, "1.2.3").bind()

    assertNull(meters.find("zone.info").gauge())
  }

  @Test
  fun `netty's memory is exported`() {
    ProcessMeters().bindTo(meters)

    assertTrue(meters.meters.any { it.id.name.startsWith("netty.allocator") }, meters.meters.map { it.id.name }.toString())
  }
}
