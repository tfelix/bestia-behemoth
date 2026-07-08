package net.bestia.zone.scenarios

import net.bestia.zone.component.HealthComponentSMSG
import net.bestia.zone.component.ManaComponentSMSG
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.battle.Mana
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.party.AlreadyInPartyException
import net.bestia.zone.party.PartyService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the party-visible [net.bestia.zone.ecs.SyncTargets] path end to end: a party member's
 * Health/Mana change must reach the other party member even without an AOI range check, while an
 * unrelated player receives nothing.
 */
class PartySyncScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: World

  @Autowired
  private lateinit var partyService: PartyService

  private var createdPartyId: Long? = null

  @Test
  @Order(1)
  fun `party member sees another members Health and Mana changes`() {
    createdPartyId = formPartyOf(clientPlayer1.connectedPlayerId, clientPlayer2.connectedPlayerId)

    val player1EntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)

    val (healthBefore, manaBefore) = world.modify(player1EntityId) { id ->
      val health = world.get(id, Health::class)!!
      val healthBefore = health.current
      health.current -= 1
      world.markChanged<Health>(id)

      // No game content attaches Mana to a master yet - add one directly to exercise the sync path.
      val mana = world.get(id, Mana::class) ?: world.add(id, Mana(current = 10, max = 10))
      val manaBefore = mana.current
      mana.current -= 1
      world.markChanged<Mana>(id)

      healthBefore to manaBefore
    }!!

    await {
      assertTrue(
        clientPlayer2.receivedAny(HealthComponentSMSG::class) {
          it.entityId == player1EntityId && it.current == healthBefore - 1
        },
        "party member should have received player1's Health update"
      )
      assertTrue(
        clientPlayer2.receivedAny(ManaComponentSMSG::class) {
          it.entityId == player1EntityId && it.current == manaBefore - 1
        },
        "party member should have received player1's Mana update"
      )
    }

    // player3 is not in the party and must not receive the owner-scoped update for player1's entity.
    assertFalse(clientPlayer3.receivedAny(HealthComponentSMSG::class) { it.entityId == player1EntityId })
    assertFalse(clientPlayer3.receivedAny(ManaComponentSMSG::class) { it.entityId == player1EntityId })
  }

  /** Disband whatever party this test created so it doesn't leak into other scenario classes
   * sharing the same Spring context (e.g. [PartyScenarios] assumes account1 starts party-less). */
  @AfterAll
  fun cleanupParty() {
    createdPartyId?.let { partyId -> partyService.disbandParty(clientPlayer1.connectedPlayerId, partyId) }
  }

  /** Puts [ownerId] and [memberId] in the same party, tolerating state left over by other scenario tests. */
  private fun formPartyOf(ownerId: Long, memberId: Long): Long {
    val partyId = try {
      partyService.createParty(ownerId, "syncTestParty").id
    } catch (_: AlreadyInPartyException) {
      // owner already parties from a previous scenario in this shared Spring context - reuse it.
      partyService.getPartyInfoForAccount(ownerId)!!.partyId
    }

    try {
      val invitation = partyService.invitePlayerToParty(ownerId, memberId)
      partyService.acceptInvitation(memberId, invitation.invitationId)
    } catch (_: AlreadyInPartyException) {
      // already partied together from a previous scenario run.
    }

    return partyId
  }
}
