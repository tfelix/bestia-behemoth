package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.party.DisbandPartySMSG
import net.bestia.zone.party.PartyException
import net.bestia.zone.party.PartyInfoSMSG
import net.bestia.zone.party.PartyInvitationSMSG
import net.bestia.zone.party.PartyService
import net.bestia.zone.party.net.AcceptPartyInviteCMSG
import net.bestia.zone.party.net.CreatePartyCMSG
import net.bestia.zone.party.net.DeclinePartyInviteCMSG
import net.bestia.zone.party.net.LeavePartyCMSG
import net.bestia.zone.party.net.RemovePartyMemberCMSG
import net.bestia.zone.party.net.RequestDisbandPartyCMSG
import net.bestia.zone.party.net.RequestPartyInfoCMSG
import net.bestia.zone.party.net.RequestPartyInvitationCMSG
import net.bestia.zone.party.net.SetPartyExpShareCMSG
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A party's life from the wire, in order: the tests share one party, created in one test and ended in the last.
 *
 * Rosters go out on a background job, so anything waiting for one is asserted inside [await].
 */
class PartyScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var partyService: PartyService

  private var declinedInvitation: PartyInvitationSMSG? = null
  private var partyId: Long = 0

  private val player2MasterId by lazy { testData.account2.masterIds.first() }

  @AfterAll
  fun leaveWhateverIsLeft() {
    // The Spring context is shared with every other scenario: a failure halfway must not leave a party behind.
    listOf(clientPlayer1, clientPlayer2, clientPlayer3).forEach { client ->
      try {
        partyService.leaveParty(client.connectedPlayerId)
      } catch (_: PartyException) {
        // Not in one.
      }
    }
  }

  @Test
  @Order(1)
  fun `requesting the party info outside a party answers with an empty roster`() {
    clientPlayer1.sendMessage(RequestPartyInfoCMSG(clientPlayer1.connectedPlayerId))

    val response = clientPlayer1.tryGetLastReceived(PartyInfoSMSG::class)

    assertNotNull(response)
    assertEquals(0L, response.partyId)
    assertTrue(response.member.isEmpty())
    assertNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class), "being in no party is not an error")
  }

  @Test
  @Order(2)
  fun `disbanding a party that does not exist is refused`() {
    clientPlayer1.sendMessage(RequestDisbandPartyCMSG(clientPlayer1.connectedPlayerId, 999_999L))

    assertEquals(OperationErrorSMSG(OpError.PARTY_NOT_IN_PARTY), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(3)
  fun `inviting outside a party is refused`() {
    clientPlayer1.sendMessage(RequestPartyInvitationCMSG(clientPlayer1.connectedPlayerId, "player2"))

    assertEquals(OperationErrorSMSG(OpError.PARTY_NOT_IN_PARTY), clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class))
  }

  @Test
  @Order(4)
  fun `creating a party sends its founder the roster`() {
    clientPlayer1.sendMessage(CreatePartyCMSG(clientPlayer1.connectedPlayerId, "myParty"))

    await {
      val response = clientPlayer1.tryGetLastReceived(PartyInfoSMSG::class)

      assertNotNull(response)
      assertEquals("myParty", response.partyName)
      assertEquals(1, response.member.size, "Must only contain owner")
      assertEquals(PartyExpShare.EVEN_SHARE, response.expShare, "a new party shares")

      val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
      assertEquals(masterEntityId, response.member[0].onlineData?.entityId)
      assertEquals(response.member[0].masterId, response.ownerMasterId)

      partyId = response.partyId
    }
  }

  @Test
  @Order(5)
  fun `creating another party while already in one is refused`() {
    clientPlayer1.sendMessage(CreatePartyCMSG(clientPlayer1.connectedPlayerId, "myParty-again"))

    assertEquals(
      OperationErrorSMSG(OpError.PARTY_ALREADY_IN_PARTY),
      clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)
    )
  }

  @Test
  @Order(6)
  fun `requesting the party info inside a party returns the roster`() {
    clientPlayer1.sendMessage(RequestPartyInfoCMSG(clientPlayer1.connectedPlayerId))

    val response = clientPlayer1.tryGetLastReceived(PartyInfoSMSG::class)

    assertNotNull(response)
    assertEquals("myParty", response.partyName)
    assertEquals(1, response.member.size, "Must only contain owner")
  }

  @Test
  @Order(7)
  fun `inviting a player by name asks them and tells the owner it went out`() {
    clientPlayer1.sendMessage(RequestPartyInvitationCMSG(clientPlayer1.connectedPlayerId, "player2"))

    assertEquals(
      OperationSuccessSMSG(OpSuccess.PARTY_INVITATION_SENT),
      clientPlayer1.tryGetLastReceived(OperationSuccessSMSG::class)
    )

    declinedInvitation = clientPlayer2.tryGetLastReceived(PartyInvitationSMSG::class)

    assertNotNull(declinedInvitation)
    assertEquals("myParty", declinedInvitation!!.partyName)
    assertEquals("player1", declinedInvitation!!.invitedByMaster)
    assertEquals(60, declinedInvitation!!.expiresInSeconds)
  }

  @Test
  @Order(8)
  fun `a declined invitation tells the owner who declined`() {
    clientPlayer2.sendMessage(DeclinePartyInviteCMSG(clientPlayer2.connectedPlayerId, declinedInvitation!!.invitationId))

    assertEquals(
      OperationErrorSMSG(OpError.PARTY_INVITE_DECLINED, listOf("player2")),
      clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)
    )
  }

  @Test
  @Order(9)
  fun `an invitation already answered cannot be answered again`() {
    clientPlayer2.sendMessage(DeclinePartyInviteCMSG(clientPlayer2.connectedPlayerId, declinedInvitation!!.invitationId))

    assertEquals(
      OperationErrorSMSG(OpError.PARTY_INVITE_EXPIRED),
      clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class)
    )
  }

  @Test
  @Order(10)
  fun `inviting a name nobody plays is refused with the name`() {
    clientPlayer1.sendMessage(RequestPartyInvitationCMSG(clientPlayer1.connectedPlayerId, "nobody"))

    assertEquals(
      OperationErrorSMSG(OpError.PARTY_PLAYER_NOT_FOUND, listOf("nobody")),
      clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)
    )
  }

  /** The owner is told as well as the newcomer: a join used to refresh only the joiner. */
  @Test
  @Order(11)
  fun `an accepted invitation gives every member the new roster`() {
    clientPlayer1.sendMessage(RequestPartyInvitationCMSG(clientPlayer1.connectedPlayerId, "player2"))
    val invitation = clientPlayer2.getLastReceived(PartyInvitationSMSG::class)

    clientPlayer2.sendMessage(AcceptPartyInviteCMSG(clientPlayer2.connectedPlayerId, invitation.invitationId))

    await {
      listOf(clientPlayer1, clientPlayer2).forEach { client ->
        val roster = client.tryGetLastReceived(PartyInfoSMSG::class)
        assertNotNull(roster)
        assertEquals(2, roster.member.size)
        assertEquals(listOf("player1", "player2"), roster.member.map { it.masterName }, "the owner first")
      }
    }
  }

  @Test
  @Order(12)
  fun `only the owner picks the EXP mode`() {
    clientPlayer2.sendMessage(SetPartyExpShareCMSG(clientPlayer2.connectedPlayerId, PartyExpShare.EACH_TAKE))

    assertEquals(
      OperationErrorSMSG(OpError.PARTY_NO_PERMISSION),
      clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class)
    )
  }

  @Test
  @Order(13)
  fun `the owner's EXP mode reaches every member`() {
    clientPlayer1.sendMessage(SetPartyExpShareCMSG(clientPlayer1.connectedPlayerId, PartyExpShare.EACH_TAKE))

    await {
      listOf(clientPlayer1, clientPlayer2).forEach { client ->
        assertEquals(PartyExpShare.EACH_TAKE, client.tryGetLastReceived(PartyInfoSMSG::class)?.expShare)
      }
    }
  }

  @Test
  @Order(14)
  fun `a kick from outside the party is refused`() {
    clientPlayer3.sendMessage(RemovePartyMemberCMSG(clientPlayer3.connectedPlayerId, player2MasterId))

    assertEquals(
      OperationErrorSMSG(OpError.PARTY_NOT_IN_PARTY),
      clientPlayer3.tryGetLastReceived(OperationErrorSMSG::class)
    )
  }

  /** The rest of the party is told too: a kick used to refresh only the owner. */
  @Test
  @Order(15)
  fun `a kicked member is told the party is over for them and the rest get the roster without them`() {
    clientPlayer1.sendMessage(RemovePartyMemberCMSG(clientPlayer1.connectedPlayerId, player2MasterId))

    await {
      assertEquals(DisbandPartySMSG(partyId), clientPlayer2.tryGetLastReceived(DisbandPartySMSG::class))
      assertEquals(1, clientPlayer1.tryGetLastReceived(PartyInfoSMSG::class)?.member?.size)
    }
  }

  @Test
  @Order(16)
  fun `the owner leaving disbands the party`() {
    clientPlayer1.sendMessage(LeavePartyCMSG(clientPlayer1.connectedPlayerId))

    await {
      assertEquals(DisbandPartySMSG(partyId), clientPlayer1.tryGetLastReceived(DisbandPartySMSG::class))
    }
    assertNull(partyService.findPartyIdOfAccount(clientPlayer1.connectedPlayerId))
  }
}
