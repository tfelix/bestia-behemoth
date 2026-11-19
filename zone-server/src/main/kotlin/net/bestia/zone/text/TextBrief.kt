package net.bestia.zone.text

/**
 * Everything a writer is told about one text. The writer may use no name and no number that is not in here; that
 * is what keeps a written text true to the world.
 */
data class TextBrief(
  /** What to write: the kind of text, its voice and its length. */
  val task: String,
  /** Every proper noun the text may use, with what it is. */
  val allowedNames: List<AllowedName>,
  /** Plain lines of what is true. */
  val facts: List<String>,
  /** Names the text must contain: the lead to where the reader goes next. */
  val mustName: List<String> = emptyList(),
  /** What the text leaves open for the game to fill in when it is shown. The text must hold each of them. */
  val placeholders: List<Placeholder> = emptyList(),
) {

  /**
   * @param name the arg of the text reference that fills it
   * @param what what will stand there, said so that a writer can build the sentence around it
   */
  data class Placeholder(val name: String, val what: String) {

    /** As it stands in the text. */
    val token: String
      get() = "{$name}"
  }

  /**
   * @param inLocale the name as other languages write it, by language code, where it differs: an epithet is
   *   translated, a town's name is not.
   */
  data class AllowedName(val name: String, val what: String, val inLocale: Map<String, String> = emptyMap())
}
