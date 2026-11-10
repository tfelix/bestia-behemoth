package net.bestia.zone.casting.ritual

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * A ritual held at a Mana Circle, on the circle's entity. It gathers helpers until its leader starts it, then runs
 * the 30-second channel of every participant, see [RitualSystem]. The spell potency page has the steps.
 */
class Ritual(
  val leaderId: EntityId,
  val leaderAccountId: AccountId,
) : Component {

  /** One helper per player, the master or one of their bestias. */
  val helpers: MutableMap<AccountId, EntityId> = mutableMapOf()

  /** Seconds of channeling left, or null while the ritual still gathers. */
  var remainingSeconds: Float? = null

  val isRunning: Boolean
    get() = remainingSeconds != null

  /** Everyone who joined, the leader first: the ones who rest afterwards. */
  val joined: List<EntityId>
    get() = listOf(leaderId) + helpers.values
}
