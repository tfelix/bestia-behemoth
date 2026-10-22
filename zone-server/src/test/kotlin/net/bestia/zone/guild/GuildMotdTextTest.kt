package net.bestia.zone.guild

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuildMotdTextTest {

  @Test
  fun `keeps any script and drops what hides itself`() {
    assertEquals("Grüße, ギルド!", GuildMotdText.cleanOrNull("  Grüße,​ ギルド!\u0007 ", 60, allowLineBreaks = false))
  }

  @Test
  fun `a title is one line, a body keeps at most one blank line`() {
    assertEquals("Raid tonight", GuildMotdText.cleanOrNull("Raid\ntonight", 60, allowLineBreaks = false))
    assertEquals("one\n\ntwo", GuildMotdText.cleanOrNull("one\r\n\n\n\n\ntwo", 60, allowLineBreaks = true))
  }

  @Test
  fun `blank clears the message, too long is refused`() {
    assertEquals("", GuildMotdText.cleanOrNull("   ", 60, allowLineBreaks = true))
    assertNull(GuildMotdText.cleanOrNull("x".repeat(61), 60, allowLineBreaks = true))
  }
}
