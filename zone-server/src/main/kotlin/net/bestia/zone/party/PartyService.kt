package net.bestia.zone.party

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs2.ZoneServer
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
  private val masterResolver: MasterResolver,
  private val zoneServer: ZoneServer,
) {
  companion object {
    const val MAX_PARTY_SIZE = 12
    const val INVITATION_TIMEOUT_SECONDS = 60L
    const val MAX_INVITATIONS_IN_FLIGHT = 100
    private val LOG = KotlinLogging.logger { }
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

    val party = Party(owner = owner, name = partyName)

    return partyRepository.save(party)
  }

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

    partyRepository.delete(party)

    return oldPartyMemberAccountIds
  }

  fun invitePlayerToParty(inviterAccountId: Long, invitedEntityId: Long): PartyInvitationSMSG {
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

    // Find invited player by entity ID - we need to add this lookup
    val invited = masterResolver.getSelectedMasterByAccountId(invitedEntityId)

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
      invitedAccountId = invited.id,
      inviterAccountId = inviter.id,
      invitation = invitation
    )

    // Schedule expiration
    scheduler.schedule({
      pendingInvitations.remove(invitationId)
    }, INVITATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    LOG.debug {
      "Created party invite $invitationId (${invitation.partyName}) for player" +
              " ${invited.id} by player ${inviter.id}"
    }

    return invitation
  }

  fun acceptInvitation(playerId: AccountId, invitationId: Long) {
    val openInvitation = pendingInvitations.remove(invitationId)
      ?: throw PartyInvitationExpired()

    val player = masterResolver.getSelectedMasterByAccountId(playerId)

    if (openInvitation.invitedAccountId != player.id) {
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

    // Add player to party
    party.member.add(player)
    partyRepository.save(party)
  }

  fun declineInvitation(playerId: Long, invitationId: Long): Long {
    val invitation = pendingInvitations.remove(invitationId)
      ?: throw PartyInvitationExpired()

    if (invitation.invitedAccountId != playerId) {
      throw PartyInviteForbiddenException(playerId, invitationId)
    }

    return invitation.inviterAccountId
  }

  @Transactional(readOnly = true)
  fun getPartyInfo(partyId: Long): PartyInfoSMSG? {
    val party = partyRepository.findByIdOrNull(partyId)
      ?: throw PartyNotFoundException(partyId)

    return buildPartyInfo(party)
  }

  @Transactional(readOnly = true)
  fun getPartyInfoForAccount(accountId: Long): PartyInfoSMSG? {
    val activeMaster = masterResolver.getSelectedMasterByAccountId(accountId)
    val party = partyRepository.findByMember(activeMaster)
      ?: return null

    return buildPartyInfo(party)
  }

  private fun buildPartyInfo(party: Party): PartyInfoSMSG {
    val partyMembers = party.member.mapNotNull { partyMemberMaster ->
      val partyMemberEntityId = findEntityIdByMaster(partyMemberMaster)
        ?: return@mapNotNull null

      zoneServer.withEntityReadLock(partyMemberEntityId) { entity ->
        val health = entity.getOrThrow(Health::class)
        val position = entity.getOrThrow(Position::class)

        PartyInfoSMSG.PartyMember(
          masterName = partyMemberMaster.name,
          onlineData = PartyInfoSMSG.PartyMember.OnlineData(
            entityId = partyMemberEntityId,
            areaName = "", // TODO: Get from ECS when this is implemented
            position = position.toVec3L(),
            hp = health
          )
        )
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
}
