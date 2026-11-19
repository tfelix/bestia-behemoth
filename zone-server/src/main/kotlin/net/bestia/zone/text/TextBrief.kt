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
) {

  data class AllowedName(val name: String, val what: String)
}
