package net.bestia.zone.text

/**
 * The names of the things a text is written about. A subject is stable in one world, so the job finds the text
 * it planned, and the game finds the text for the thing it is about to show.
 */
object TextSubject {

  private const val GRAVE = "grave:"
  private const val QUEST = "quest:"
  private const val NEWS = "news:"

  /** The epitaph on the named grave of a figure of the chronicle. */
  fun grave(figure: Int): String {
    return "$GRAVE$figure"
  }

  fun figureOfGrave(subject: String): Int? {
    return subject.removePrefix(GRAVE).takeIf { subject.startsWith(GRAVE) }?.toIntOrNull()
  }

  /** What the giver of a quest plan says when asked about it. */
  fun questOffer(planId: Long): String {
    return "$QUEST$planId:offer"
  }

  /** The line of one step of a quest plan in the quest log. */
  fun questStep(planId: Long, step: Int): String {
    return "$QUEST$planId:step:$step"
  }

  /**
   * One of the ways a townsperson asks for news of [kind] to be carried on. Written before any such news exists,
   * so it names no town: the game fills in the place.
   */
  fun newsOffer(kind: String, variant: Int): String {
    return "$NEWS$kind:$variant"
  }

  /** The kind of news and the variant a news subject names. */
  fun newsPartsOf(subject: String): Pair<String, Int>? {
    val parts = subject.removePrefix(NEWS).takeIf { subject.startsWith(NEWS) }?.split(":") ?: return null
    val variant = parts.getOrNull(1)?.toIntOrNull() ?: return null

    return parts[0] to variant
  }

  fun isQuest(subject: String): Boolean {
    return subject.startsWith(QUEST)
  }

  /** The plan and, for a step text, the step a quest subject names. */
  fun questPartsOf(subject: String): Pair<Long, Int?>? {
    val parts = subject.removePrefix(QUEST).takeIf { isQuest(subject) }?.split(":") ?: return null
    val planId = parts.firstOrNull()?.toLongOrNull() ?: return null

    return planId to parts.getOrNull(2)?.toIntOrNull()
  }
}
