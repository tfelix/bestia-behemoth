package net.bestia.zone.party

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.identity.GroupMember
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Who a party-wide line reaches, such as party chat: every member, the sender included, played or not. */
@Component
class PartyAudience(
  private val sessions: ConnectionInfoService,
  private val world: WorldView,
  private val masters: MasterRepository,
) {

  /** The members of the party of the master [accountId] is playing, or null when it is in none. Off the tick. */
  fun of(accountId: AccountId): List<GroupMember>? {
    val masterEntityId = try {
      sessions.getSelectedMasterEntityId(accountId)
    } catch (_: NoActiveSessionException) {
      return null
    }

    val membership = world.read { get(masterEntityId, PartyMembership::class) } ?: return null

    return membership.members.map { GroupMember(it.accountId, it.masterId) }
  }

  /**
   * The members of [masterId]'s party, or null when it is in none, read from the database so it answers for a
   * master nobody is playing. The database is the truth here: the party commits before its components follow.
   */
  @Transactional(readOnly = true)
  fun ofMaster(masterId: Long): List<GroupMember>? {
    val party = masters.findByIdOrNull(masterId)?.party ?: return null

    return party.everyone.map { GroupMember(it.account.id, it.id) }
  }
}
