package net.bestia.zone.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Other players read these names, so a name must look like exactly what it is. */
class DisplayNameTest {

  @Test
  fun `a direction override is refused`() {
    assertNull(DisplayName.normalizeOrNull("Bob‮nimda", MAX))
  }

  @Test
  fun `a look-alike letter from another script is refused`() {
    assertNull(DisplayName.normalizeOrNull("Вob", MAX))
  }

  @Test
  fun `a control character is refused`() {
    assertNull(DisplayName.normalizeOrNull("Bo\nb", MAX))
  }

  @Test
  fun `runs of spaces count as one`() {
    assertEquals("Big Bob", DisplayName.normalizeOrNull("  Big   Bob ", MAX))
  }

  @Test
  fun `a blank or too long name is refused`() {
    assertNull(DisplayName.normalizeOrNull("   ", MAX))
    assertNull(DisplayName.normalizeOrNull("a".repeat(MAX + 1), MAX))
  }

  private companion object {
    const val MAX = 20
  }
}
