package net.bestia.zone.townsfolk.knowledge

/**
 * How a speaker came to know a memory, which decides how they frame it.
 *
 * Old knowledge said plainly reads like a chronicle. Said as "my grandfather told me", it reads like a
 * person - and it warns a player that the details may have shifted on the way.
 */
enum class Certainty {

  /** Somebody alive saw it. Said plainly. */
  FIRSTHAND,

  /** Handed down in a family: "my grandfather told me". */
  TOLD,

  /** Older than any family's memory: "an old saying goes". */
  LEGEND;

  companion object {

    /**
     * @param handedDown whether only one household keeps it. A family keeps its own stories longer
     *   than a town does, so such a memory is never a mere saying.
     */
    fun of(era: Era, handedDown: Boolean): Certainty {
      return when {
        era == Era.RECENT || era == Era.LIVING -> FIRSTHAND
        handedDown || era == Era.GENERATIONS -> TOLD
        else -> LEGEND
      }
    }
  }
}
