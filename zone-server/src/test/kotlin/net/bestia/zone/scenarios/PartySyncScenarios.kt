package net.bestia.zone.scenarios

import net.bestia.zone.battle.ecs.status.HealthComponentSMSG
import net.bestia.zone.battle.ecs.status.ManaComponentSMSG
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.party.AlreadyInPartyException
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.party.PartyService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import net.bestia.zone.ecs.core.EcsWorld

/**
 * Exercises the party-visible [net.bestia.zone.sync.SyncTargets] path end to end: a party member's
 * Health/Mana change must reach the other party member even without an AOI range check, while an
 * unrelated player receives nothing.
 */
class PartySyncScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: EcsWorld

  @Autowired
  private lateinit var partyService: PartyService

  private var createdPartyId: Long? = null

  @Test
  @Order(1)
  fun `party member sees another members Health and Mana changes`() {
    createdPartyId = formPartyOf(clientPlayer1.connectedPlayerId, clientPlayer2.connectedPlayerId)

    val player1EntityId = connectionInfoService.getSelectedMasterEntityId(clientPlayer1.connectedPlayerId)

    world.modify(player1EntityId) { id ->
      world.get(id, Health::class)!!.current -= 1 // the setter marks Health dirty itself

      // No game content attaches Mana to a master yet - add one directly to exercise the sync path.
      val mana = world.get(id, Mana::class) ?: world.add(id, Mana(current = 10, max = 10))
      mana.current -= 1 // the setter marks Mana dirty itself
    }

    // Compared with what the entity holds now, not with "before - 1": a sync carries only the value at the end
    // of its tick, so a regeneration that runs in the same tick as the change above replaces it on the wire.
    await {
      val (health, mana) = world.read {
        get(player1EntityId, Health::class)!!.current to get(player1EntityId, Mana::class)!!.current
      }

      assertEquals(
        health,
        clientPlayer2.tryGetLastReceivedFor(HealthComponentSMSG::class, player1EntityId)?.current,
        "party member should have received player1's Health update"
      )
      assertEquals(
        mana,
        clientPlayer2.tryGetLastReceivedFor(ManaComponentSMSG::class, player1EntityId)?.current,
        "party member should have received player1's Mana update"
      )
    }

    // player3 is not in the party and must not receive the owner-scoped update for player1's entity.
    assertFalse(clientPlayer3.receivedAny(HealthComponentSMSG::class) { it.entityId == player1EntityId })
    assertFalse(clientPlayer3.receivedAny(ManaComponentSMSG::class) { it.entityId == player1EntityId })
  }

  /** Nothing else gives a spawned master its roster, and without it party members stop hearing of it. */
  @Test
  @Order(2)
  fun `a member who logs in again still belongs to the party`() {
    val account = clientPlayer2.connectedPlayerId
    val entityId = connectionInfoService.getSelectedMasterEntityId(account)

    clientPlayer2.disconnect()
    await { assertFalse(world.read { isAlive(entityId) }, "the master should have left the world") }

    clientPlayer2.connect(testData.account2.masterIds.first())

    assertEquals(createdPartyId, world.read { get(entityId, PartyMembership::class)?.partyId })
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
