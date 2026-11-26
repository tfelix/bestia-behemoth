package net.bestia.api.bugreport

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LogScrubberTest {

  @Test
  fun `credentials, emails and addresses are masked`() {
    val log = """
      login as ayla@example.com from 192.168.1.20
      GET /map Authorization: Ticket 3f2c9a1e-8d7b-4c6a-9e5f-1a2b3c4d5e6f
      token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0MiJ9.c2lnbmF0dXJl
    """.trimIndent()

    assertEquals(
      """
      login as <email> from <ip>
      GET /map Authorization: Ticket <secret>
      token <jwt>
      """.trimIndent(),
      LogScrubber.scrub(log),
    )
  }

  @Test
  fun `versions and game text are kept`() {
    val log = "Godot 4.7.1.stable, driver 32.0.15.6094, Ayla hits Wolf for 12"

    assertEquals(log, LogScrubber.scrub(log))
  }
}
