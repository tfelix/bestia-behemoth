package net.bestia.zone.chat

/** What any chat line may hold, however it reached the zone: typed in the game client or in the companion app. */
object ChatText {

  /** Must match `max_length` of the ChatInput in the client's Chat.tscn, and the app's chat input. */
  const val MAX_TEXT_LENGTH = 200

  private val INVISIBLE_FORMATTING =
    ('\u200B'..'\u200F') + ('\u202A'..'\u202E') + ('\u2066'..'\u2069') + '\uFEFF'

  /** Control and direction-override characters only ever garble, or disguise, someone else's chat window. */
  fun withoutControlCharacters(text: String): String {
    return text.filterNot { Character.isISOControl(it) || it in INVISIBLE_FORMATTING }
  }

  /** A line no client sends: every client caps its input at [MAX_TEXT_LENGTH], so a longer one is forged. */
  fun isTooLong(text: String): Boolean {
    return text.codePointCount(0, text.length) > MAX_TEXT_LENGTH
  }
}
