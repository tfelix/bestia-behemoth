package net.bestia.zone.guild

import java.text.Normalizer

/**
 * The rule for a guild's message of the day: free text in any script, but nothing that hides itself or rearranges what other
 * members read - no control characters and no format characters such as direction overrides.
 */
object GuildMotdText {

  private val BLANK_LINES = Regex("\n{3,}")

  /** [raw] as stored, or null when it is longer than [maxLength]. Blank is allowed: it clears it. */
  fun cleanOrNull(raw: String, maxLength: Int, allowLineBreaks: Boolean): String? {
    val unixText = raw.replace("\r\n", "\n")
    val shaped = if (allowLineBreaks) unixText else unixText.replace('\n', ' ')
    val text = Normalizer.normalize(shaped, Normalizer.Form.NFC)
      .filter { it == '\n' || isShown(it) }
      .replace(BLANK_LINES, "\n\n")
      .trim()

    return text.takeIf { it.length <= maxLength }
  }

  private fun isShown(c: Char): Boolean {
    return !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT.toInt()
  }
}
