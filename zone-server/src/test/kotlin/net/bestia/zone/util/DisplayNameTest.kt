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

  @Test
  fun `words keep letters, digits and one space between them`() {
    assertEquals("Big Bob2", DisplayName.normalizeWordsOrNull("  Big   Bob2 ", MAX))
    assertEquals("Bob", DisplayName.normalizeWordsOrNull(" Bob ", MAX))
  }

  @Test
  fun `words refuse a second space`() {
    listOf("Big Bob 2", "a b c", "René Zoë Åsa").forEach { assertNull(DisplayName.normalizeWordsOrNull(it, MAX), it) }
  }

  @Test
  fun `words refuse punctuation and symbols`() {
    listOf("Bob!", "Bob-Bob", "Bob_2", "O'Neil", "Bob.", "a,b", "Bob@home", "100%", "a+b", "Bob×Bob", "Bob÷Bob")
      .forEach { assertNull(DisplayName.normalizeWordsOrNull(it, MAX), it) }
  }

  @Test
  fun `words accept Latin accents`() {
    listOf("René", "Åsa", "Zoë", "Straße", "Łukasz", "Søren", "Çağrı", "Ñandú")
      .forEach { assertEquals(it, DisplayName.normalizeWordsOrNull(it, MAX), it) }
  }

  @Test
  fun `a decomposed accent is composed into one letter`() {
    val decomposed = "René"

    assertEquals("René", DisplayName.normalizeWordsOrNull(decomposed, MAX))
  }

  @Test
  fun `a lone combining mark is refused`() {
    assertNull(DisplayName.normalizeWordsOrNull("́Bob", MAX))
    assertNull(DisplayName.normalizeWordsOrNull("B́́́ob", MAX))
  }

  @Test
  fun `words still refuse look-alikes, control characters and other scripts`() {
    assertNull(DisplayName.normalizeWordsOrNull("Вob", MAX))
    assertNull(DisplayName.normalizeWordsOrNull("Bob‮nimda", MAX))
    assertNull(DisplayName.normalizeWordsOrNull("Bo\nb", MAX))
    assertNull(DisplayName.normalizeWordsOrNull("Bob Bob", MAX))
    assertNull(DisplayName.normalizeWordsOrNull("Ｂob", MAX))
    assertNull(DisplayName.normalizeWordsOrNull("Bo​b", MAX))
  }

  @Test
  fun `words are limited after composing`() {
    assertEquals("e".repeat(MAX), DisplayName.normalizeWordsOrNull("e".repeat(MAX), MAX))
    assertNull(DisplayName.normalizeWordsOrNull("e".repeat(MAX + 1), MAX))
    assertEquals("è".repeat(MAX), DisplayName.normalizeWordsOrNull("è".repeat(MAX), MAX))
  }

  @Test
  fun `a blank words name is refused`() {
    assertNull(DisplayName.normalizeWordsOrNull("   ", MAX))
  }

  /** A party's name is not limited to words, so punctuation stays valid there. */
  @Test
  fun `the plain rule still allows punctuation`() {
    assertEquals("Bob's Crew!", DisplayName.normalizeOrNull("Bob's Crew!", MAX))
  }

  private companion object {
    const val MAX = 24
  }
}
