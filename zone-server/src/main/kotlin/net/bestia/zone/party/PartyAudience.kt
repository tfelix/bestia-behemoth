package net.bestia.zone.party

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/** Who a party-wide line reaches, such as party chat: every member being played, the sender included. */
@Component
class PartyAudience(
  private val sessions: ConnectionInfoService,
  private val world: WorldView,
) {

  /** The accounts playing a member of [accountId]'s party, or null when it is in none. Off the tick. */
  fun of(accountId: AccountId): Set<AccountId>? {
    val masterEntityId = try {
      sessions.getSelectedMasterEntityId(accountId)
    } catch (_: NoActiveSessionException) {
      return null
    }

    val membership = world.read { get(masterEntityId, PartyMembership::class) } ?: return null

    return membership.members
      .filter { sessions.isPlaying(it.accountId, it.masterId) }
      .mapTo(HashSet()) { it.accountId }
  }
}
