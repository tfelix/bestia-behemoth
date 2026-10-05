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

/**
 * Invitation ids are counted up from 1, so anyone can guess the ones in flight. Only the invited player may
 * answer one.
 */
class PartyInvitationTest {

  private val owner = master(id = 10, accountId = OWNER)
  private val invited = master(id = 20, accountId = INVITED)
  private val stranger = master(id = 30, accountId = STRANGER)

  private val party = mockk<Party>(relaxed = true) {
    every { id } returns PARTY_ID
    every { owner } returns this@PartyInvitationTest.owner
    every { size } returns 1
    every { name } returns "party"
    every { member } returns mutableSetOf()
  }

  private val partyRepository = mockk<PartyRepository>(relaxed = true) {
    every { findByOwner(owner) } returns party
    every { findByMember(any()) } returns null
    every { findById(PARTY_ID) } returns Optional.of(party)
  }

  private val masterRepository = mockk<MasterRepository> {
    every { save(any<Master>()) } answers { firstArg() }
  }

  private val masterResolver = mockk<MasterResolver>(relaxed = true) {
    every { getSelectedMasterByAccountId(OWNER) } returns owner
    every { getSelectedMasterByAccountId(INVITED) } returns invited
    every { getSelectedMasterByAccountId(STRANGER) } returns stranger
    every { getSelectedMasterEntityIdByAccountId(any()) } returns null
  }

  private val service = PartyService(
    partyRepository,
    masterRepository,
    masterResolver,
    ZoneConfig(bestiaBaseSlotCount = 1, bestiaMaxSlotCount = 1, jwtAuthSecretKey = "", shardId = 1),
    mockk(relaxed = true)
  )

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

  private fun master(id: Long, accountId: Long): Master {
    return mockk(relaxed = true) {
      every { this@mockk.id } returns id
      every { account.id } returns accountId
      every { party } returns null
      every { name } returns "master$id"
    }
  }

  private companion object {
    const val OWNER = 1L
    const val INVITED = 2L
    const val STRANGER = 3L
    const val PARTY_ID = 5L
  }
}
