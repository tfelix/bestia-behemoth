package net.bestia.zone.battle.status

/** Why a status effect left its bearer, so a script can tell running out from being torn off. */
enum class RemovalReason {
  EXPIRED,
  DISPELLED,

  /** Its own rule ended it, like a freeze that shatters on the first hit. */
  BROKEN,
}
