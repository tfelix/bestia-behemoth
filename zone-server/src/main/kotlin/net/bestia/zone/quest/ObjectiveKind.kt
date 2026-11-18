package net.bestia.zone.quest

enum class ObjectiveKind {
  DELIVER_ITEM,
  KILL,

  /** Read or look closely at the objective's target. */
  EXAMINE,

  /** Speak to the objective's target, a townsperson. */
  TALK,

  /**
   * Hand the objective's item over at its target. The source that made the quest takes the goods and counts
   * them, as a board does for a delivery.
   */
  BRING,

  /** Pick one of the objective's choices. What follows may depend on it. */
  DECIDE,
}
