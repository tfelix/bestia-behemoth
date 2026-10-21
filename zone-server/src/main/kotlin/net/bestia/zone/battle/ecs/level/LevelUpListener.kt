package net.bestia.zone.battle.ecs.level

import net.bestia.zone.util.AccountId

/**
 * Told when a player's master or one of its bestias gains a level, so the slices above can refresh what shows
 * it, such as a party roster.
 *
 * Called on the tick, once per level-up tick and entity whatever the number of levels gained. It must only hand
 * work off: no database, no blocking.
 */
fun interface LevelUpListener {
  /** [masterId] is the master the entity is or belongs to. */
  fun leveledUp(accountId: AccountId, masterId: Long, isMaster: Boolean)
}
