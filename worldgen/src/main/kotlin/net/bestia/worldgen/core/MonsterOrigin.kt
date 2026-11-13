package net.bestia.worldgen.core

/** What woke a world boss. Each but [ANCIENT] cites an earlier event, so the beast has a cause in the log. */
enum class MonsterOrigin {
  /** The mana that pooled where the star fell. */
  WOUND,

  /** A tomb that was broken into. */
  ROBBED_TOMB,

  /** A volcano's eruption. */
  ERUPTION,

  /** A cult that called it. */
  SUMMONED,

  /** The dead of a great battlefield. */
  RISEN_DEAD,

  /** Nothing the chronicle knows of: it was always there. */
  ANCIENT
}
