package net.bestia.zone.postal

import net.bestia.zone.postal.persistence.Letter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LetterTextTest {

  @Test
  fun `a body keeps its line breaks and loses every other control character`() {
    val text = LetterText.of(" Hello ", "Dear friend,\n\tthe bridge‮ is down.\n")

    assertEquals(LetterText("Hello", "Dear friend,\nthe bridge is down."), text)
  }

  @Test
  fun `a letter with neither subject nor body is refused`() {
    assertNull(LetterText.of("  ", "\n\n"))
  }

  @Test
  fun `a subject or body longer than the form allows is refused`() {
    assertNull(LetterText.of("x".repeat(Letter.MAX_SUBJECT_LENGTH + 1), "body"))
    assertNull(LetterText.of("subject", "x".repeat(Letter.MAX_BODY_LENGTH + 1)))
  }
}
