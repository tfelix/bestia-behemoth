package net.bestia.zone.party

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.MasterResolver
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.Party
import net.bestia.zone.account.persistence.PartyRepository
import net.bestia.zone.config.PartyRulesConfig
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.persistence.afterCommit
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.NoActiveSessionException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.DisplayName
import net.bestia.zone.util.EntityId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Every change to a party locks its row first, then touches its masters. Master rows are written with
 * `@DynamicUpdate`, so a party change writes only `party_id` and cannot undo a level the persister just saved.
 *
 * Tells nobody: the handlers hand what changed to [PartyRosterPublisher], which is the one path rosters take.
 */
@Service
class PartyService(
  private val partyRepository: PartyRepository,
  private val masterRepository: MasterRepository,
  private val masterResolver: MasterResolver,
  private val rules: PartyRulesConfig,
  private val world: WorldView,
  private val sessions: ConnectionInfoService,
) {

  companion object {
    private val LOG = KotlinLogging.logger { }
  }

  /** What leaving or removal turned a membership into, so the handler knows whom to tell what. */
  sealed class LeavePartyResult {
    abstract val partyId: Long

    /** The party ended. [members] were in it, whoever ended it included. */
    data class Disbanded(override val partyId: Long, val members: Set<PartyMembership.Member>) : LeavePartyResult()

    /** [member] is out of a party that goes on. */
    data class Left(override val partyId: Long, val member: PartyMembership.Member) : LeavePartyResult()
  }

  data class Declined(val inviterAccountId: AccountId, val invitedMasterName: String)

  /** An invitation to send to [invitedAccountId]. */
  data class SentInvitation(val invitedAccountId: AccountId, val invitation: PartyInvitationSMSG)

  private inner class OpenPartyInvitation(
    val inviterAccountId: Long,
    val invitedAccountId: Long,
    val invitedMasterId: Long,
    val invitedMasterName: String,
    val invitation: PartyInvitationSMSG,
    private val createdAtNanos: Long = System.nanoTime(),
  ) {
    fun isExpired(): Boolean {
      return System.nanoTime() - createdAtNanos > TimeUnit.SECONDS.toNanos(rules.invitationTimeoutSeconds)
    }
  }

  /** Expired invitations are dropped when they are next touched, so no thread has to watch the clock. */
  private val pendingInvitations = ConcurrentHashMap<Long, OpenPartyInvitation>()
  private val nextInvitationId = AtomicLong(1L)

  @Transactional
  fun createParty(ownerAccountId: AccountId, partyName: String): Party {
    val owner = masterResolver.getSelectedMasterByAccountId(ownerAccountId)

    if (owner.party != null) {
      throw AlreadyInPartyException()
    }

    val name = DisplayName.normalizeOrNull(partyName, rules.nameMaxLength)
      ?: throw InvalidPartyNameException()

    // An identity id inserts on save, so a taken name surfaces here rather than at commit.
    val saved = try {
      partyRepository.save(Party(owner = owner, name = name))
    } catch (_: DataIntegrityViolationException) {
      throw PartyNameInUseException(name)
    }

    syncPartyMembershipComponents(saved)

    return saved
  }

  @Transactional
  fun disbandParty(requesterAccountId: AccountId, partyId: Long): LeavePartyResult.Disbanded {
    val party = partyRepository.findByIdForUpdateOrThrow(partyId)
    val requester = masterResolver.getSelectedMasterByAccountId(requesterAccountId)

    if (party.owner.id != requester.id) {
      throw NotPartyOwnerException()
    }

    return disband(party)
  }

  private fun disband(party: Party): LeavePartyResult.Disbanded {
    val members = party.everyone.map { it.asMember() }.toSet()

    // Master.party still references this party - clear it first, otherwise Hibernate flushes those managed
    // Masters with a dangling reference to the row we're about to delete.
    party.member.forEach { member -> member.party = null }
    party.owner.party = null
    masterRepository.saveAll(party.member + party.owner)

    partyRepository.delete(party)

    members.forEach { clearPartyMembershipComponent(it) }

    return LeavePartyResult.Disbanded(party.id, members)
  }

  /** The owner invites the master named [invitedMasterName], which must be being played and in no party. */
  @Transactional(readOnly = true)
  fun invitePlayerToParty(inviterAccountId: AccountId, invitedMasterName: String): SentInvitation {
    pendingInvitations.values.removeIf { it.isExpired() }

    val inviter = masterResolver.getSelectedMasterByAccountId(inviterAccountId)
    val party = partyRepository.findByOwner(inviter)
      ?: if (inviter.party != null) throw NotPartyOwnerException() else throw NotPartyException()

    val name = invitedMasterName.trim()
    val invited = masterRepository.findByName(name)
      ?.takeIf { sessions.isPlaying(it.account.id, it.id) }
      ?: throw PartyPlayerNotFoundException(name)

    // Covers inviting oneself too: the inviter is in this very party.
    if (invited.party != null) {
      throw PartyTargetInPartyException(invited.name)
    }

    val invitation = synchronized(pendingInvitations) {
      openInvitationTo(party.id, invited.id)?.invitation
        ?: openInvitation(party, inviter, invited)
    }

    return SentInvitation(invited.account.id, invitation)
  }

  private fun openInvitationTo(partyId: Long, invitedMasterId: Long): OpenPartyInvitation? {
    return pendingInvitations.values.firstOrNull {
      it.invitation.partyId == partyId && it.invitedMasterId == invitedMasterId && !it.isExpired()
    }
  }

  /** Open invitations take a seat each, so no party can crowd the server with more than it could seat. */
  private fun openInvitation(party: Party, inviter: Master, invited: Master): PartyInvitationSMSG {
    val openInvitations = pendingInvitations.values.count { it.invitation.partyId == party.id }
    if (party.size + openInvitations >= rules.maxSize) {
      throw PartyFullException()
    }

    val invitationId = nextInvitationId.getAndIncrement()

    val invitation = PartyInvitationSMSG(
      invitedByMaster = inviter.name,
      partyId = party.id,
      partyName = party.name,
      invitationId = invitationId,
      expiresInSeconds = rules.invitationTimeoutSeconds.toInt(),
    )

    pendingInvitations[invitationId] = OpenPartyInvitation(
      inviterAccountId = inviter.account.id,
      invitedAccountId = invited.account.id,
      invitedMasterId = invited.id,
      invitedMasterName = invited.name,
      invitation = invitation
    )

    LOG.debug {
      "Created party invite $invitationId (${invitation.partyName}) for player" +
        " ${invited.account.id} by player ${inviter.account.id}"
    }

    return invitation
  }

  /** @return the party joined */
  @Transactional
  fun acceptInvitation(accountId: AccountId, invitationId: Long): Long {
    val openInvitation = takeInvitation(accountId, invitationId)

    // The invitation was for the master the account played then; switching to another one ends it.
    if (!sessions.isPlaying(accountId, openInvitation.invitedMasterId)) {
      throw PartyInvitationExpired()
    }

    // Locked, or two players accepting into the last free seat both find the party not full.
    val party = partyRepository.findByIdForUpdate(openInvitation.invitation.partyId)
      ?: throw PartyNotFoundException(openInvitation.invitation.partyId)

    if (party.size >= rules.maxSize) {
      throw PartyFullException()
    }

    val player = masterRepository.findByIdOrNull(openInvitation.invitedMasterId)
      ?: throw PartyInvitationExpired()

    if (player.party != null) {
      throw AlreadyInPartyException()
    }

    // Master.party is the owning side of the relationship (Party.member is mappedBy) - it must be
    // set on the member itself for the membership to actually persist.
    player.party = party
    masterRepository.save(player)
    party.member.add(player)

    syncPartyMembershipComponents(party)

    return party.id
  }

  fun declineInvitation(accountId: AccountId, invitationId: Long): Declined {
    val invitation = takeInvitation(accountId, invitationId)

    return Declined(invitation.inviterAccountId, invitation.invitedMasterName)
  }

  /** Checks before removing: the ids are guessable, so anyone else could otherwise burn the invitation. */
  private fun takeInvitation(accountId: AccountId, invitationId: Long): OpenPartyInvitation {
    val invitation = pendingInvitations[invitationId]?.takeUnless { it.isExpired() }
      ?: throw PartyInvitationExpired()

    if (invitation.invitedAccountId != accountId) {
      LOG.warn { "Account $accountId tried to answer invitation $invitationId, which is not addressed to it" }

      throw PartyInviteForbiddenException(accountId, invitationId)
    }

    // Two answers racing each other: only one of them takes it.
    if (!pendingInvitations.remove(invitationId, invitation)) {
      throw PartyInvitationExpired()
    }

    return invitation
  }

  /**
   * The owner removes a member from their own party. The owner cannot remove themself this way - use
   * [leaveParty], which disbands the party instead since there is no ownership transfer.
   */
  @Transactional
  fun removeMember(requesterAccountId: AccountId, memberMasterId: Long): LeavePartyResult.Left {
    val requester = masterResolver.getSelectedMasterByAccountId(requesterAccountId)
    val partyId = requester.party?.id ?: throw NotPartyException()
    val party = partyRepository.findByIdForUpdateOrThrow(partyId)

    if (party.owner.id != requester.id) {
      throw NotPartyOwnerException()
    }

    val member = party.member.find { it.id == memberMasterId && it.id != party.owner.id }
      ?: throw NotPartyMemberException(masterRepository.findByIdOrNull(memberMasterId)?.name ?: "")

    party.member.remove(member)
    member.party = null
    masterRepository.save(member)

    val removed = member.asMember()
    clearPartyMembershipComponent(removed)
    syncPartyMembershipComponents(party)

    return LeavePartyResult.Left(party.id, removed)
  }

  /**
   * A member (or the owner) leaves their current party. There is no ownership transfer: if the owner leaves,
   * the whole party is disbanded instead.
   */
  @Transactional
  fun leaveParty(accountId: AccountId): LeavePartyResult {
    val player = masterResolver.getSelectedMasterByAccountId(accountId)
    val party = player.party?.let { partyRepository.findByIdForUpdate(it.id) } ?: throw NotPartyException()

    if (party.owner.id == player.id) {
      return disband(party)
    }

    party.member.remove(player)
    player.party = null
    masterRepository.save(player)

    val left = player.asMember()
    clearPartyMembershipComponent(left)
    syncPartyMembershipComponents(party)

    return LeavePartyResult.Left(party.id, left)
  }

  /** The owner picks how the party shares kill EXP. @return the party changed */
  @Transactional
  fun setExpShare(accountId: AccountId, expShare: PartyExpShare): Long {
    val requester = masterResolver.getSelectedMasterByAccountId(accountId)
    val partyId = requester.party?.id ?: throw NotPartyException()
    val party = partyRepository.findByIdForUpdateOrThrow(partyId)

    if (party.owner.id != requester.id) {
      throw NotPartyOwnerException()
    }

    party.expShare = expShare
    syncPartyMembershipComponents(party)

    return party.id
  }

  /**
   * Unhooks a master from its party right before the master row itself is deleted, and reports who is
   * left needing to hear about it.
   *
   * Separate from [leaveParty] because that resolves the acting master through the session, and a master
   * being deleted is by definition not the selected one - deletion happens on the character selection
   * screen. Owning the party disbands it, matching [leaveParty]: there is no ownership transfer. The deleted
   * master is not being played, so the roster publisher tells it nothing.
   *
   * Must run before the master is deleted, not after: `Party.owner` is a non-null FK and `Master.party`
   * is the owning side of the membership, so a master removed with either still pointing at it takes a
   * constraint violation rather than a clean delete.
   */
  @Transactional
  fun detachDeletedMaster(master: Master): LeavePartyResult? {
    val partyId = partyRepository.findPartyIdOfMaster(master.id) ?: return null
    val party = partyRepository.findByIdForUpdate(partyId) ?: return null

    if (party.owner.id == master.id) {
      return disband(party)
    }

    party.member.removeIf { it.id == master.id }
    master.party = null
    // Not saved here - the caller is about to delete this master anyway, and writing it back first
    // would only queue an update for a row that is on its way out.

    val left = master.asMember()
    clearPartyMembershipComponent(left)
    syncPartyMembershipComponents(party)

    return LeavePartyResult.Left(party.id, left)
  }

  /** The party of the master [accountId] plays, or null. */
  @Transactional(readOnly = true)
  fun findPartyIdOfAccount(accountId: AccountId): Long? {
    val masterId = sessions.activeMasterIdOf(accountId) ?: return null

    return partyRepository.findPartyIdOfMaster(masterId)
  }

  /**
   * Refreshes the [PartyMembership] component on every played member's master entity, owner included, once the
   * change has committed: a rolled back change must not leave the component ahead of the row. Members not being
   * played get the roster when they next log in.
   */
  private fun syncPartyMembershipComponents(party: Party) {
    val membership = party.membership()

    afterCommit {
      membership.members.forEach { member ->
        onPlayedMaster(member) { id -> add(id, membership) }
      }
    }
  }

  /** Removes the [PartyMembership] component from a (now former) member's entity, once the change has committed. */
  private fun clearPartyMembershipComponent(member: PartyMembership.Member) {
    afterCommit {
      onPlayedMaster(member) { id -> remove(id, PartyMembership::class) }
    }
  }

  private fun onPlayedMaster(member: PartyMembership.Member, block: World.(EntityId) -> Unit) {
    if (!sessions.isPlaying(member.accountId, member.masterId)) {
      return
    }

    val entityId = try {
      sessions.getSelectedMasterEntityId(member.accountId)
    } catch (_: NoActiveSessionException) {
      return
    }

    world.modify(entityId) { id -> block(id) }
  }
}
