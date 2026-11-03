package net.bestia.zone.battle.status

/** Something a status effect may forbid its bearer to do, checked by [net.bestia.zone.battle.ActionGuard]. */
enum class ActionLock {
  /** Move requests. A rooted body is also slowed to a halt by its effect, so a path in progress stops too. */
  MOVE,
  ATTACK,
  CAST,
  ITEM;

  companion object {
    val ALL: Set<ActionLock> = entries.toSet()
  }
}
