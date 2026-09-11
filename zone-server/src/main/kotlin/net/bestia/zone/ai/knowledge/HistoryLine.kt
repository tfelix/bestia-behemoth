package net.bestia.zone.ai.knowledge

/**
 * How one kind of event may be spoken about.
 *
 * Not the words. [variants] is how many phrasings the translation file carries for it, and [slots] the
 * placeholders those phrasings may use - which is the server's whole interest in a sentence it will
 * never read.
 */
data class HistoryLine(
  val variants: Int,
  val slots: Set<String>,
)
