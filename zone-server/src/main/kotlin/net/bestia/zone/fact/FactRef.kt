package net.bestia.zone.fact

/**
 * One thing a player can know about the world: an [aspect] of a record of the chronicle, or a clue.
 * [index] is the record's index in its list of the chronicle, so a fact only means something in its world.
 */
data class FactRef(val subject: Subject, val index: Long, val aspect: Aspect) {

  /** The `ActorType`s of the chronicle, its events, and the clues a quest lays out. */
  enum class Subject { CIV, SETTLEMENT, FIGURE, ARTIFACT, SITE, DEITY, CULT, MONSTER, EVENT, CLUE }

  enum class Aspect {
    NAME,
    PLACE,

    /** What became of it. */
    FATE,

    /** The whole of an event or a clue, which has no parts to learn one by one. */
    TALE,
  }

  companion object {

    fun event(eventId: Int): FactRef {
      return FactRef(Subject.EVENT, eventId.toLong(), Aspect.TALE)
    }
  }
}
