package net.bestia.zone.battle.status

/** Something an entity did that an effect on it may answer, like a wound that opens with every swing. */
enum class EntityAction {
  ATTACKED,
  CAST,
  USED_ITEM,

  /** Set off on a walk of its own; being pushed or blinked somewhere is not counted. */
  MOVED,
}
