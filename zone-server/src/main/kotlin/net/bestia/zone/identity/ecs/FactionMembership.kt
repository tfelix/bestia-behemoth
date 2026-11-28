package net.bestia.zone.identity.ecs

import net.bestia.worldgen.core.Faction
import net.bestia.zone.ecs.core.Component
import java.time.Instant

/**
 * The Order a master is sworn to. Lives on the master row and outlasts the world, like honor.
 *
 * A master who left an Order stays unsworn until [unswornUntil] and no Order takes them back before.
 */
data class FactionMembership(
  val faction: Faction? = null,
  val swornAt: Instant? = null,
  val unswornUntil: Instant? = null,
) : Component {

  val isSworn: Boolean
    get() {
      return faction != null
    }

  fun mayPetition(now: Instant): Boolean {
    return !isSworn && (unswornUntil == null || !now.isBefore(unswornUntil))
  }
}
