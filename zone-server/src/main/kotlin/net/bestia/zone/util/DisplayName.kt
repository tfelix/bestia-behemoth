package net.bestia.zone.util

/**
 * The rule for a name other players read, such as a master's or a party's. Printable ASCII with single spaces: a
 * name must not hide control or direction characters, pass for another with a look-alike letter, or differ from
 * another only in its spacing.
 */
object DisplayName {

  private val SPACES = Regex(" +")

  /** The name as it is stored, or null when it is blank, longer than [maxLength] or not printable ASCII. */
  fun normalizeOrNull(raw: String, maxLength: Int): String? {
    val name = raw.trim().replace(SPACES, " ")

    if (name.isEmpty() || name.length > maxLength || !name.all { it.code in PRINTABLE_ASCII }) {
      return null
    }

    return name
  }

  private val PRINTABLE_ASCII = 32..126
}
