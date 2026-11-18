package net.bestia.zone.quest

enum class ObjectiveKind {
  DELIVER_ITEM,
  KILL,

  /** Read or look closely at the objective's target. */
  EXAMINE,

  /** Speak to the objective's target, a townsperson. */
  TALK,

  /** Pick one of the objective's choices. What follows may depend on it. */
  DECIDE,
}
