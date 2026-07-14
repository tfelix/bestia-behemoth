package net.bestia.zone.socket

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneReadinessServiceTest {

  @Test
  fun `starts not ready and flips to ready once marked`() {
    val service = ZoneReadinessService()

    assertFalse(service.isReady(), "zone must not accept logins before boot completes")

    service.markReady()

    assertTrue(service.isReady())
  }
}
