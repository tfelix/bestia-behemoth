package net.bestia.worldgen.core

/**
 * A story the history began and never ended: the raw material for a quest.
 *
 * Appended to rather than reordered, like every other chronicle vocabulary.
 */
enum class ThreadKind {
  /** Nobody holds it, nobody keeps it, and nobody laid it to rest. Actors: the artifact, and its site if known. */
  ARTIFACT_LOST,

  /** The town that first kept it still stands and wants it back. Actors: the artifact and that town. */
  ARTIFACT_AWAY,

  /** An oath of vengeance nobody settled. Actors: the avenger, the slain, and the people sworn against. */
  VENGEANCE_UNFULFILLED,

  /** A world boss bound in a seal that still holds. Actors: the monster and its seal. */
  BOSS_SEALED,

  /** A world boss that withdrew to its lair or vanished. Actors: the monster and its lair. */
  BOSS_AT_LARGE,

  /** A ruling house that ended with nobody of its blood alive. Actors: its last ruler and their people. */
  HOUSE_WITHOUT_HEIR,

  /** A cult its people turned on that is still kept, in hiding. Actors: the cult and its persecutors. */
  CULT_PERSECUTED,

  /**
   * A god no standing town, living people or kept cult worships any more. Actors: the god and its last shrine, a
   * ruined town it was patron of or the sanctuary of a stamped-out cult.
   */
  FORGOTTEN_GOD
}
