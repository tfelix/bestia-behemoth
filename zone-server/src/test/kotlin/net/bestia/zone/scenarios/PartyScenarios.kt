package net.bestia.zone.scenarios

import net.bestia.zone.party.*
import net.bestia.zone.ecs.session.ConnectionInfoService
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PartyScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  private var partyInvitationSMSGToDeclined: PartyInvitationSMSG? = null
  private var partyInvitationCreatedSMSG: PartyInvitationCreatedSMSG? = null
  private var createdPartyInfoSMSG: PartyInfoSMSG? = null

  @Test
  @Order(1)
  fun `requesting a party info while not part of a party returns an error`() {
    clientPlayer1.sendMessage(RequestPartyInfoCMSG(clientPlayer1.connectedPlayerId))

    val response = clientPlayer1.tryGetLastReceived(PartyErrorSMSG::class)

    assertNotNull(response)
    assertEquals(PartyErrorSMSG.PartyErrorCode.NO_PARTY, response.error)
  }

  @Test
  @Order(2)
  fun `requesting a party disband while not part of a party returns an error`() {
    clientPlayer1.sendMessage(RequestDisbandPartyCMSG(clientPlayer1.connectedPlayerId, 1L))

    val response = clientPlayer1.tryGetLastReceived(PartyErrorSMSG::class)

    assertNotNull(response)
    assertEquals(PartyErrorSMSG.PartyErrorCode.NO_PARTY, response.error)
  }

  @Test
  @Order(3)
  fun `inviting a party member while not part of a party returns an error`() {
    clientPlayer1.sendMessage(RequestPartyInvitationCMSG(clientPlayer1.connectedPlayerId, 100L))

    val response = clientPlayer1.tryGetLastReceived(PartyErrorSMSG::class)

    assertNotNull(response)
    assertEquals(PartyErrorSMSG.PartyErrorCode.NO_PARTY, response.error)
  }

  @Test
  @Order(4)
  fun `creating a party creates a party returns the info`() {
    clientPlayer1.sendMessage(
      CreatePartyCMSG(
        clientPlayer1.connectedPlayerId,
        "myParty"
      )
    )

    val response = clientPlayer1.tryGetLastReceived(PartyInfoSMSG::class)

    assertNotNull(response)
    assertEquals("myParty", response.partyName)
    assertEquals(1, response.member.size, "Must only contain owner")

    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    assertEquals(masterEntityId, response.member[0].onlineData?.entityId)

    createdPartyInfoSMSG = response
  }

  @Test
  @Order(5)
  fun `creating another party while already in one returns an error`() {
    clientPlayer1.sendMessage(
      CreatePartyCMSG(
        clientPlayer1.connectedPlayerId,
        "myParty-again"
      )
    )

    val response = clientPlayer1.tryGetLastReceived(PartyErrorSMSG::class)

    assertNotNull(response)
    assertEquals(PartyErrorSMSG.PartyErrorCode.ALREADY_IN_PARTY, response.error)
  }

  @Test
  @Order(6)
  fun `requesting a party info while inside a part returns the info`() {
    clientPlayer1.sendMessage(RequestPartyInfoCMSG(clientPlayer1.connectedPlayerId))

    val response = clientPlayer1.tryGetLastReceived(PartyInfoSMSG::class)

    assertNotNull(response)
    assertEquals("myParty", response.partyName)
    assertEquals(1, response.member.size, "Must only contain owner")

    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)
    assertEquals(masterEntityId, response.member[0].onlineData?.entityId)
  }

  @Test
  @Order(7)
  fun `inviting a player while inside a party forwards invite to player`() {
    clientPlayer1.sendMessage(
      RequestPartyInvitationCMSG(
        clientPlayer1.connectedPlayerId,
        clientPlayer2.connectedPlayerId
      )
    )

    partyInvitationCreatedSMSG = clientPlayer1.tryGetLastReceived(PartyInvitationCreatedSMSG::class)
    assertNotNull(partyInvitationCreatedSMSG)
    assertEquals(clientPlayer2.connectedPlayerId, partyInvitationCreatedSMSG!!.invitedPlayerEntityId)
    assertEquals(PartyInvitationCreatedSMSG.InvitationStatus.CREATED, partyInvitationCreatedSMSG!!.status)

    partyInvitationSMSGToDeclined = clientPlayer2.tryGetLastReceived(PartyInvitationSMSG::class)

    assertNotNull(partyInvitationSMSGToDeclined)
    assertEquals("myParty", partyInvitationSMSGToDeclined!!.partyName)
    assertEquals("masterName", partyInvitationSMSGToDeclined!!.invitedByMaster)
  }

  @Test
  @Order(8)
  fun `when player declines invitation an error is send to original requester`() {
    clientPlayer2.sendMessage(
      DeclinePartyInviteCMSG(
        playerId = clientPlayer2.connectedPlayerId,
        invitationId = partyInvitationSMSGToDeclined!!.invitationId
      )
    )

    val response = clientPlayer1.tryGetLastReceived(PartyInviteDeclinedSMSG::class)

    assertNotNull(response)
    assertEquals(partyInvitationCreatedSMSG!!.invitationId, response.invitationId)
  }

  @Test
  @Order(9)
  fun `when player waits for too long and the invitation expired he gets an error on accept`() {
    clientPlayer1.sendMessage(
      RequestPartyInvitationCMSG(
        clientPlayer1.connectedPlayerId,
        clientPlayer2.connectedPlayerId
      )
    )

    // TODO simulate the timeout.

    clientPlayer2.sendMessage(
      DeclinePartyInviteCMSG(
        playerId = clientPlayer2.connectedPlayerId,
        invitationId = partyInvitationSMSGToDeclined!!.invitationId
      )
    )

    val response = clientPlayer2.tryGetLastReceived(PartyErrorSMSG::class)

    assertNotNull(response)
    assertEquals(PartyErrorSMSG.PartyErrorCode.INVITE_EXPIRED, response.error)
  }

  /*
  @Test
  @Order(9)
  fun `when player accepts party invitation in time he gets added to the party`() {
    clientPlayer1.sendMessage(
      RequestPartyInvitation(
        clientPlayer1.connectedPlayerId,
        clientPlayer2.connectedPlayerId
      )
    )

    clientPlayer2.sendMessage(
      AcceptPartyInvite(
        playerId = clientPlayer2.connectedPlayerId,
        invitationId = partyInvitationToDeclined!!.invitationId
      )
    )

    // Party info is send to all clients so it updates the party information with the new user.
    val player1PartyInfo = clientPlayer1.tryGetLastReceived(PartyInfo::class)
    val player2PartyInfo = clientPlayer2.tryGetLastReceived(PartyInfo::class)

    assertNotNull(player1PartyInfo)
    assertNotNull(player2PartyInfo)

    assertEquals("myParty", player2PartyInfo.partyName)
    assertEquals(1L, player2PartyInfo.partyId)
    assertEquals(2, player2PartyInfo.member.size)
    // first one is owner of party.
    assertEquals("master name", player2PartyInfo.member[0].masterName)
    assertEquals("area name", player2PartyInfo.member[0].areaName)
    assertEquals(Vec3.ZERO, player2PartyInfo.member[0].position)
    assertEquals(10, player2PartyInfo.member[0].hp.max)
    assertEquals(10, player2PartyInfo.member[0].hp.current)
  }

  @Test
  @Order(10)
  fun `when a non party member requests the removal of a party member it returns an error`() {
    clientPlayer3.sendMessage(
      RequestPartyMemberRemoval(
        clientPlayer3.connectedPlayerId,
        createdPartyInfo!!.partyId,
        123
      )
    )

    // Party info is to all clients so it updates the party information with the new user.
    val response = clientPlayer3.tryGetLastReceived(PartyError::class)

    assertNotNull(response)
    assertEquals(PartyError.PartyErrorCode.NO_PERMISSION, response.error)
  }

  @Test
  @Order(11)
  fun `when a non party member requests to disband of a party member it is declined`() {
    clientPlayer3.sendMessage(
      RequestDisbandParty(
        clientPlayer3.connectedPlayerId,
        createdPartyInfo!!.partyId
      )
    )

    // Party info is send to all clients so it updates the party information with the new user.
    val response = clientPlayer3.tryGetLastReceived(PartyError::class)

    assertNotNull(response)
    assertEquals(PartyError.PartyErrorCode.NO_PERMISSION, response.error)
  }

  @Test
  @Order(12)
  fun `when a party member requests to remove another party member it is declined`() {
    // Add the third user
    clientPlayer1.sendMessage(
      RequestPartyInvitation(
        clientPlayer1.connectedPlayerId,
        clientPlayer3.connectedPlayerId
      )
    )

    val invitation = clientPlayer3.tryGetLastReceived(PartyInvitation::class)

    assertNotNull(invitation)

    clientPlayer3.sendMessage(
      AcceptPartyInvite(
        playerId = clientPlayer3.connectedPlayerId,
        invitationId = invitation.partyId
      )
    )

    clientPlayer3.sendMessage(
      RequestPartyMemberRemoval(
        clientPlayer3.connectedPlayerId,
        createdPartyInfo!!.partyId,
        clientPlayer2.connectedPlayerId
      )
    )

    // Should be declined
    val response = clientPlayer3.tryGetLastReceived(PartyError::class)

    assertNotNull(response)
    assertEquals(PartyError.PartyErrorCode.NO_PERMISSION, response.error)
  }

  @Test
  @Order(13)
  fun `when the party owner requests party info it works`() {
    // TODO()
  }

  @Test
  @Order(14)
  fun `when a party member requests party info it works`() {
    // TODO()
  }

  @Test
  @Order(15)
  fun `when a non party member requests party info it is declined`() {
    // TODO()
  }

  @Test
  @Order(16)
  fun `when the party owner removes another member it works`() {
    // TODO()
  }

  @Test
  @Order(17)
  fun `when a party member removes himself it works`() {
    // TODO()
  }

  @Test
  @Order(18)
  fun `when the party owner disbands a party existing members are removed and notified`() {
    // TODO()
  }*/
}
