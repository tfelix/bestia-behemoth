package net.bestia.zone.util

import java.text.Normalizer

/**
 * The rule for a name other players read, such as a master's or a party's. Printable ASCII with single spaces: a
 * name must not hide control or direction characters, pass for another with a look-alike letter, or differ from
 * another only in its spacing.
 */
object DisplayName {

  private val SPACES = Regex(" +")

  /** The name as it is stored, or null when it is blank, longer than [maxLength] or not printable ASCII. */
  fun normalizeOrNull(raw: String, maxLength: Int): String? {
    return normalize(raw, maxLength) { it.code in PRINTABLE_ASCII }
  }

  /**
   * The name as it is stored, or null when it is blank, longer than [maxLength] or more than words: only letters,
   * digits and spaces, no punctuation or symbols. A letter may carry a Latin accent, so "René" and "Åsa" pass while
   * "Вob" with a Cyrillic "В" does not.
   */
  fun normalizeWordsOrNull(raw: String, maxLength: Int): String? {
    // Composed first, so an "e" followed by a combining grave is the one letter "è" and not a refused mark.
    return normalize(Normalizer.normalize(raw, Normalizer.Form.NFC), maxLength, ::isWordChar)
  }

  private fun normalize(raw: String, maxLength: Int, isAllowed: (Char) -> Boolean): String? {
    val name = raw.trim().replace(SPACES, " ")

    if (name.isEmpty() || name.length > maxLength || !name.all(isAllowed)) {
      return null
    }

    return name
  }

  private fun isWordChar(c: Char): Boolean {
    return c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == ' ' || (c in ACCENTED_LATIN && c.isLetter())
  }

  private val PRINTABLE_ASCII = 32..126

  /** Latin-1 Supplement and Latin Extended-A, where `isLetter` leaves out the signs × and ÷ sitting among the letters. */
  private val ACCENTED_LATIN = 'À'..'ž'
}
