package net.bestia.login.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A display name is what recovery looks an account up by and what a passkey picker shows. Two names that look the
 * same must be the same name.
 */
class DisplayNamesTest {

  @Test
  fun `a name with a Cyrillic letter that looks Latin is refused`() {
    assertNull(DisplayNames.normalizeOrNull("Аdmin"))
  }

  @Test
  fun `invisible characters are refused`() {
    assertNull(DisplayNames.normalizeOrNull("Ad​min"))
  }

  @Test
  fun `runs of spaces count as one`() {
    assertEquals("Big Bob", DisplayNames.normalizeOrNull("  Big   Bob "))
  }

  @Test
  fun `a plain name is kept as it is`() {
    assertEquals("Player_1-x", DisplayNames.normalizeOrNull("Player_1-x"))
  }

  @Test
  fun `a name outside the length limits is refused`() {
    assertNull(DisplayNames.normalizeOrNull("ab"))
    assertNull(DisplayNames.normalizeOrNull("a".repeat(33)))
  }
}
