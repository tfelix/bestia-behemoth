package net.bestia.zone.party

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ZoneConfig
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.MasterResolver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import kotlin.test.assertEquals

/**
 * Invitation ids are counted up from 1, so anyone can guess the ones in flight. Only the invited player may
 * answer one, and no single party may crowd out everyone else's.
 */
class PartyInvitationTest {

  private val masters = HashMap<Long, Master>()

  private val partyRepository = mockk<PartyRepository>(relaxed = true) {
    every { findByMember(any()) } returns null
  }

  private val masterRepository = mockk<MasterRepository> {
    every { save(any<Master>()) } answers { firstArg() }
  }

  private val masterResolver = mockk<MasterResolver>(relaxed = true) {
    every { getSelectedMasterByAccountId(any()) } answers { masterOf(firstArg()) }
    every { getSelectedMasterEntityIdByAccountId(any()) } returns null
  }

  private val service = PartyService(
    partyRepository,
    masterRepository,
    masterResolver,
    ZoneConfig(bestiaBaseSlotCount = 1, bestiaMaxSlotCount = 1, jwtAuthSecretKey = "", shardId = 1),
    mockk(relaxed = true)
  )

  init {
    partyOwnedBy(OWNER, partyId = 5L)
  }

  @Test
  fun `a stranger accepting an invitation leaves it open for the invited player`() {
    val invitation = service.invitePlayerToParty(OWNER, INVITED)

    assertThrows<PartyInviteForbiddenException> { service.acceptInvitation(STRANGER, invitation.invitationId) }

    assertDoesNotThrow { service.acceptInvitation(INVITED, invitation.invitationId) }
  }

  @Test
  fun `a stranger declining an invitation leaves it open for the invited player`() {
    val invitation = service.invitePlayerToParty(OWNER, INVITED)

    assertThrows<PartyInviteForbiddenException> { service.declineInvitation(STRANGER, invitation.invitationId) }

    assertDoesNotThrow { service.acceptInvitation(INVITED, invitation.invitationId) }
  }

  @Test
  fun `a party has no more open invitations than free seats`() {
    val freeSeats = PartyService.MAX_PARTY_SIZE - 1
    repeat(freeSeats) { service.invitePlayerToParty(OWNER, 100L + it) }

    assertThrows<PartyFullException> { service.invitePlayerToParty(OWNER, 100L + freeSeats) }
  }

  @Test
  fun `one party's invitations do not lock out other parties`() {
    partyOwnedBy(OTHER_OWNER, partyId = 6L)
    repeat(200) { runCatching { service.invitePlayerToParty(OWNER, 100L + it) } }

    assertDoesNotThrow { service.invitePlayerToParty(OTHER_OWNER, INVITED) }
  }

  @Test
  fun `inviting the same player twice keeps one invitation`() {
    val first = service.invitePlayerToParty(OWNER, INVITED)
    val second = service.invitePlayerToParty(OWNER, INVITED)

    assertEquals(first.invitationId, second.invitationId)
  }

  private fun partyOwnedBy(accountId: Long, partyId: Long) {
    val owner = masterOf(accountId)
    val party = mockk<Party>(relaxed = true) {
      every { id } returns partyId
      every { this@mockk.owner } returns owner
      every { size } returns 1
      every { name } returns "party$partyId"
      every { member } returns mutableSetOf()
    }
    every { partyRepository.findByOwner(owner) } returns party
    every { partyRepository.findById(partyId) } returns Optional.of(party)
  }

  private fun masterOf(accountId: Long): Master {
    return masters.getOrPut(accountId) {
      mockk(relaxed = true) {
        every { id } returns accountId * 10
        every { account.id } returns accountId
        every { party } returns null
        every { name } returns "master$accountId"
      }
    }
  }

  private companion object {
    const val OWNER = 1L
    const val INVITED = 2L
    const val STRANGER = 3L
    const val OTHER_OWNER = 4L
  }
}
