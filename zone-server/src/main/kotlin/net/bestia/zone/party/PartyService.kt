package net.bestia.zone.party

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ZoneConfig
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.WorldView
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Service
class PartyService(
  private val partyRepository: PartyRepository,
  private val masterRepository: MasterRepository,
  private val masterResolver: MasterResolver,
  private val zoneConfig: ZoneConfig,
  private val world: WorldView,
) {

  companion object {
    const val MAX_PARTY_SIZE = 12
    const val INVITATION_TIMEOUT_SECONDS = 60L
    const val MAX_INVITATIONS_IN_FLIGHT = 100
    private val LOG = KotlinLogging.logger { }
  }

  /** Result of [leaveParty]: distinguishes a full disband (requester was the owner) from a plain
   * self-removal, since the handler needs to notify a different set of accounts either way. */
  sealed class LeavePartyResult {
    data class Disbanded(val partyId: Long, val notifiedAccountIds: List<Long>) : LeavePartyResult()
    data class Left(val partyId: Long, val remainingMemberAccountIds: Set<Long>) : LeavePartyResult()
  }

  private class OpenPartyInvitation(
    val inviterAccountId: Long,
    val invitedAccountId: Long,
    val invitation: PartyInvitationSMSG
  )

  private val pendingInvitations = ConcurrentHashMap<Long, OpenPartyInvitation>()
  private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
  private var nextInvitationId = 1L

  @Transactional
  fun createParty(ownerId: Long, partyName: String): Party {
    val owner = masterResolver.getSelectedMasterByAccountId(ownerId)

    if (owner.party != null) {
      throw AlreadyInPartyException()
    }

    val trimmedName = partyName.trim()
    if (trimmedName.isEmpty() ||
      trimmedName.length > zoneConfig.partyNameMaxLength ||
      !trimmedName.all { it.code in 32..126 }
    ) {
      throw InvalidPartyNameException()
    }

    val party = Party(owner = owner, name = trimmedName)
    val saved = partyRepository.save(party)

    syncPartyMembershipComponents(saved)

    return saved
  }

  @Transactional
  fun disbandParty(requesterId: Long, partyId: Long): List<Long> {
    val party = partyRepository.findByIdOrThrow(partyId)
    val requester = masterResolver.getSelectedMasterByAccountId(requesterId)

    // only owner can disband
    if (party.owner.id != requester.id) {
      throw NotPartyOwnerException()
    }

    // Notify all members
    val oldPartyMemberAccountIds = party.member.map { member ->
      member.account.id
    }
    val allAffectedAccountIds = oldPartyMemberAccountIds + party.owner.account.id

    // Master.party/ownedParty still reference this party - clear them first, otherwise Hibernate
    // flushes those managed Masters with a dangling reference to the row we're about to delete.
    party.member.forEach { member -> member.party = null }
    party.owner.party = null
    masterRepository.saveAll(party.member + party.owner)

    partyRepository.delete(party)

    allAffectedAccountIds.forEach { clearPartyMembershipComponent(it) }

    return oldPartyMemberAccountIds
  }

  @Transactional(readOnly = true)
  fun invitePlayerToParty(inviterAccountId: Long, invitedAccountId: Long): PartyInvitationSMSG {
    if (pendingInvitations.size > MAX_INVITATIONS_IN_FLIGHT) {
      throw TooManyPartyInvitationsInFlightException()
    }

    val inviter = masterResolver.getSelectedMasterByAccountId(inviterAccountId)

    val party = partyRepository.findByOwner(inviter)
      ?: throw NotPartyException()

    if (party.owner.id != inviter.id) {
      throw NotPartyOwnerException()
    }

    if (party.size >= MAX_PARTY_SIZE) {
      throw PartyFullException()
    }

    val invited = masterResolver.getSelectedMasterByAccountId(invitedAccountId)

    val existingParty = partyRepository.findByMember(invited)
    if (existingParty != null) {
      throw AlreadyInPartyException()
    }

    val invitationId = nextInvitationId++

    val invitation = PartyInvitationSMSG(
      invitedByMaster = inviter.name,
      partyId = party.id,
      partyName = party.name,
      invitationId = invitationId
    )

    pendingInvitations[invitationId] = OpenPartyInvitation(
      invitedAccountId = invited.account.id,
      inviterAccountId = inviter.account.id,
      invitation = invitation
    )

    // Schedule expiration
    scheduler.schedule({
      pendingInvitations.remove(invitationId)
    }, INVITATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    LOG.debug {
      "Created party invite $invitationId (${invitation.partyName}) for player" +
              " ${invited.account.id} by player ${inviter.account.id}"
    }

    return invitation
  }

  @Transactional
  fun acceptInvitation(playerId: AccountId, invitationId: Long) {
    val openInvitation = pendingInvitations.remove(invitationId)
      ?: throw PartyInvitationExpired()

    val player = masterResolver.getSelectedMasterByAccountId(playerId)

    if (openInvitation.invitedAccountId != player.account.id) {
      LOG.warn { "Invitation $openInvitation was attempted to be accepted by player $player" }

      throw PartyInviteForbiddenException(playerId, invitationId)
    }

    val party = partyRepository.findByIdOrNull(openInvitation.invitation.partyId)

    if (party == null) {
      LOG.warn {
        "Party ${openInvitation.invitation.partyId} in invitation " +
                "${openInvitation.invitation.invitationId} did not exist"
      }

      throw PartyNotFoundException(openInvitation.invitation.partyId)
    }

    if (party.size >= MAX_PARTY_SIZE) {
      throw PartyFullException()
    }

    if (player.party != null) {
      throw AlreadyInPartyException()
    }

    // Master.party is the owning side of the relationship (Party.member is mappedBy) - it must be
    // set on the member itself for the membership to actually persist.
    player.party = party
    masterRepository.save(player)
    party.member.add(player)

    syncPartyMembershipComponents(party)
  }

  fun declineInvitation(playerId: Long, invitationId: Long): Long {
    val invitation = pendingInvitations.remove(invitationId)
      ?: throw PartyInvitationExpired()

    if (invitation.invitedAccountId != playerId) {
      throw PartyInviteForbiddenException(playerId, invitationId)
    }

    return invitation.inviterAccountId
  }

  /** Owner-initiated removal of a party member. The owner cannot remove themself this way - use
   * [leaveParty], which disbands the party instead since there is no ownership transfer. */
  @Transactional
  fun removeMember(requesterId: Long, partyId: Long, memberAccountId: Long): Long {
    val party = partyRepository.findByIdOrThrow(partyId)
    val requester = masterResolver.getSelectedMasterByAccountId(requesterId)

    if (party.owner.id != requester.id) {
      throw NotPartyOwnerException()
    }

    val member = party.member.find { it.account.id == memberAccountId }
      ?: throw NotPartyMemberException(memberAccountId)

    party.member.remove(member)
    member.party = null
    masterRepository.save(member)

    clearPartyMembershipComponent(memberAccountId)
    syncPartyMembershipComponents(party)

    return member.account.id
  }

  /** A member (or the owner) leaves their current party. There is no ownership transfer: if the
   * owner leaves, the whole party is disbanded instead. */
  @Transactional
  fun leaveParty(playerId: Long): LeavePartyResult {
    val player = masterResolver.getSelectedMasterByAccountId(playerId)
    val party = player.party ?: throw NotPartyException()

    return if (party.owner.id == player.id) {
      val notified = disbandParty(playerId, party.id)
      LeavePartyResult.Disbanded(party.id, notified)
    } else {
      party.member.remove(player)
      player.party = null
      masterRepository.save(player)

      clearPartyMembershipComponent(playerId)
      syncPartyMembershipComponents(party)

      val remaining = party.member.map { it.account.id }.toSet() + party.owner.account.id
      LeavePartyResult.Left(party.id, remaining)
    }
  }

  @Transactional(readOnly = true)
  fun getPartyInfo(partyId: Long): PartyInfoSMSG? {
    val party = partyRepository.findByIdOrNull(partyId)
      ?: throw PartyNotFoundException(partyId)

    return buildPartyInfo(party)
  }

  @Transactional(readOnly = true)
  fun getPartyInfoForAccount(accountId: Long): PartyInfoSMSG? {
    val party = findPartyOfAccount(accountId) ?: return null

    return buildPartyInfo(party)
  }

  /** Resolves the party of [accountId]'s active master, whether they are the owner or a plain
   * member - [PartyRepository.findByMember] alone misses the owner, who isn't in `Party.member`. */
  @Transactional(readOnly = true)
  fun findPartyOfAccount(accountId: Long): Party? {
    val activeMaster = masterResolver.getSelectedMasterByAccountId(accountId)

    return partyRepository.findByOwner(activeMaster) ?: partyRepository.findByMember(activeMaster)
  }

  private fun buildPartyInfo(party: Party): PartyInfoSMSG {
    val allMembers = party.member + party.owner
    val partyMembers = allMembers.map { partyMemberMaster ->
      val partyMemberEntityId = findEntityIdByMaster(partyMemberMaster)

      if (partyMemberEntityId == null) {
        PartyInfoSMSG.PartyMember(masterName = partyMemberMaster.name, onlineData = null)
      } else {
        world.modify(partyMemberEntityId) { id ->
          val health = getOrThrow(id, Health::class)
          val position = getOrThrow(id, Position::class)

          PartyInfoSMSG.PartyMember(
            masterName = partyMemberMaster.name,
            onlineData = PartyInfoSMSG.PartyMember.OnlineData(
              entityId = partyMemberEntityId,
              areaName = "", // TODO: Get from ECS when this is implemented
              position = position.toVec3L(),
              hp = health
            )
          )
        } ?: PartyInfoSMSG.PartyMember(masterName = partyMemberMaster.name, onlineData = null)
      }
    }

    return PartyInfoSMSG(
      partyId = party.id,
      partyName = party.name,
      member = partyMembers
    )
  }

  private fun findEntityIdByMaster(partyMaster: Master): EntityId? {
    val entityId = try {
      masterResolver.getEntityIdByMasterId(partyMaster.id)
    } catch (_: Exception) {
      // master was not found
      null
    }

    if (entityId == null) {
      LOG.debug { "Entity ID for master ${partyMaster.name} (${partyMaster.id}) not found" }
    }

    return entityId
  }

  /** Refreshes the [PartyMembership] component on every currently-online member's (owner
   * included) entity with the current roster. Offline members are skipped - they pick up the
   * current roster the next time they log in and their master entity is spawned. */
  private fun syncPartyMembershipComponents(party: Party) {
    val allAccountIds = (party.member.map { it.account.id } + party.owner.account.id).toSet()

    allAccountIds.forEach { accountId ->
      val entityId = masterResolver.getSelectedMasterEntityIdByAccountId(accountId) ?: return@forEach
      world.modify(entityId) { id -> add(id, PartyMembership(party.id, allAccountIds)) }
    }
  }

  /** Removes the [PartyMembership] component from a specific (now former) member's entity. */
  private fun clearPartyMembershipComponent(accountId: Long) {
    val entityId = masterResolver.getSelectedMasterEntityIdByAccountId(accountId) ?: return
    world.modify(entityId) { id -> remove(id, PartyMembership::class) }
  }
}
