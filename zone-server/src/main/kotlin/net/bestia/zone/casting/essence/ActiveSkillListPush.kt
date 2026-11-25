package net.bestia.zone.casting.essence

import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * Sends a player the skill list of an entity again, if that is the entity the player acts as. The skills window shows
 * whichever list arrives, so a list of another entity would replace the one on screen. A port: the list is built in
 * `master`.
 */
fun interface ActiveSkillListPush {
  fun pushIfActive(accountId: AccountId, entityId: EntityId)
}
