package net.bestia.zone.account.persistence

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.MasterResolver
import net.bestia.zone.config.PartyRulesConfig
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.party.PartyFullException
import net.bestia.zone.party.PartyInvitationExpired
import net.bestia.zone.party.PartyInviteForbiddenException
import net.bestia.zone.party.PartyPlayerNotFoundException
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.PartyTargetInPartyException
import net.bestia.zone.session.ConnectionInfoService
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
  private val rules = PartyRulesConfig()

  /** Accounts whose master is being played; everyone but [OFFLINE]. */
  private val sessions = mockk<ConnectionInfoService>(relaxed = true) {
    every { isPlaying(any(), any()) } answers { firstArg<Long>() != OFFLINE }
  }

  private val partyRepository = mockk<PartyRepository>(relaxed = true) {
    every { findByMember(any()) } returns null
  }

  private val masterRepository = mockk<MasterRepository> {
    every { save(any<Master>()) } answers { firstArg() }
    every { findByName(any()) } answers { masters.values.firstOrNull { it.name == firstArg<String>() } }
    every { findById(any()) } answers { Optional.ofNullable(masters.values.firstOrNull { it.id == firstArg<Long>() }) }
  }

  private val masterResolver = mockk<MasterResolver>(relaxed = true) {
    every { getSelectedMasterByAccountId(any()) } answers { masterOf(firstArg()) }
  }

  private val service = PartyService(
    partyRepository,
    masterRepository,
    masterResolver,
    rules,
    mockk(relaxed = true),
    sessions,
  )

  init {
    partyOwnedBy(OWNER, partyId = 5L)
    listOf(INVITED, STRANGER, OFFLINE).forEach { masterOf(it) }
  }

  @Test
  fun `a stranger accepting an invitation leaves it open for the invited player`() {
    val sent = service.invitePlayerToParty(OWNER, nameOf(INVITED))

    assertThrows<PartyInviteForbiddenException> { service.acceptInvitation(STRANGER, sent.invitation.invitationId) }

    assertDoesNotThrow { service.acceptInvitation(INVITED, sent.invitation.invitationId) }
  }

  @Test
  fun `a stranger declining an invitation leaves it open for the invited player`() {
    val sent = service.invitePlayerToParty(OWNER, nameOf(INVITED))

    assertThrows<PartyInviteForbiddenException> { service.declineInvitation(STRANGER, sent.invitation.invitationId) }

    assertDoesNotThrow { service.acceptInvitation(INVITED, sent.invitation.invitationId) }
  }

  @Test
  fun `the invitation goes to the account playing the named master`() {
    val sent = service.invitePlayerToParty(OWNER, nameOf(INVITED))

    assertEquals(INVITED, sent.invitedAccountId)
    assertEquals(rules.invitationTimeoutSeconds.toInt(), sent.invitation.expiresInSeconds)
  }

  /** One answer for both, as for whispers: telling them apart would make inviting a way to ask who is online. */
  @Test
  fun `an unknown name and a master nobody plays are refused alike`() {
    assertThrows<PartyPlayerNotFoundException> { service.invitePlayerToParty(OWNER, "nobody") }
    assertThrows<PartyPlayerNotFoundException> { service.invitePlayerToParty(OWNER, nameOf(OFFLINE)) }
  }

  @Test
  fun `the owner cannot invite themself`() {
    every { masterOf(OWNER).party } returns mockk(relaxed = true)

    assertThrows<PartyTargetInPartyException> { service.invitePlayerToParty(OWNER, nameOf(OWNER)) }
  }

  @Test
  fun `an invitation is void once the account plays another master`() {
    val sent = service.invitePlayerToParty(OWNER, nameOf(INVITED))
    every { sessions.isPlaying(INVITED, any()) } returns false

    assertThrows<PartyInvitationExpired> { service.acceptInvitation(INVITED, sent.invitation.invitationId) }
  }

  @Test
  fun `a party has no more open invitations than free seats`() {
    val freeSeats = rules.maxSize - 1
    repeat(freeSeats) { service.invitePlayerToParty(OWNER, nameOf(100L + it)) }

    assertThrows<PartyFullException> { service.invitePlayerToParty(OWNER, nameOf(100L + freeSeats)) }
  }

  @Test
  fun `one party's invitations do not lock out other parties`() {
    partyOwnedBy(OTHER_OWNER, partyId = 6L)
    repeat(200) { runCatching { service.invitePlayerToParty(OWNER, nameOf(100L + it)) } }

    assertDoesNotThrow { service.invitePlayerToParty(OTHER_OWNER, nameOf(INVITED)) }
  }

  @Test
  fun `inviting the same player twice keeps one invitation`() {
    val first = service.invitePlayerToParty(OWNER, nameOf(INVITED))
    val second = service.invitePlayerToParty(OWNER, nameOf(INVITED))

    assertEquals(first.invitation.invitationId, second.invitation.invitationId)
  }

  private fun partyOwnedBy(accountId: Long, partyId: Long) {
    val owner = masterOf(accountId)
    val party = mockk<Party>(relaxed = true) {
      every { id } returns partyId
      every { this@mockk.owner } returns owner
      every { size } returns 1
      every { name } returns "party$partyId"
      every { member } returns mutableSetOf()
      every { everyone } returns listOf(owner)
      every { expShare } returns PartyExpShare.EVEN_SHARE
    }
    every { partyRepository.findByOwner(owner) } returns party
    every { partyRepository.findByIdForUpdate(partyId) } returns party
  }

  /** Created on first use, so a test can name masters beyond the ones it set up. */
  private fun nameOf(accountId: Long): String = masterOf(accountId).name

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
    const val OFFLINE = 5L
  }
}
