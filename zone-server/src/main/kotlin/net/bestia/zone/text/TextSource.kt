package net.bestia.zone.text

/**
 * A part of the game that wants texts written for the world. It names the subjects, and for each one it hands the
 * writer what the text may say. Off the tick.
 */
interface TextSource {

  /** Every text this source wants for the world as it is now. */
  fun wanted(): List<Wanted>

  /** What the writer may say about one of this source's subjects, or null when the subject is gone or not its own. */
  fun briefOf(subject: String): TextBrief?

  class Wanted(val subject: String, val priority: Int)
}
