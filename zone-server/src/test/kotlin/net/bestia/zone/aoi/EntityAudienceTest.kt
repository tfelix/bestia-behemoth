package net.bestia.zone.aoi

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntityAudienceTest {

  private val world = testWorld()
  private val visibility = RecordingEntityVisibility()
  private val sessions = ConnectionInfoService()
  private val sut = EntityAudience(visibility, sessions)

  private fun masterEntity(masterId: Long): EntityId {
    return world.createEntity { id ->
      add(id, Account(OWNER))
      add(id, Master(masterId))
    }
  }

  private fun bestiaOf(masterId: Long): EntityId {
    return world.createEntity { id ->
      add(id, Account(OWNER))
      add(id, OwnedBestia(masterId, playerBestiaId = 21L))
    }
  }

  @Test
  fun `the player hears about their selected master out of sight`() {
    val master = masterEntity(MASTER_ID)
    sessions.activateSession(OWNER, MASTER_ID, master)

    assertEquals(setOf(OWNER), sut.of(world, master))
  }

  @Test
  fun `the player hears about a bestia of their selected master out of sight`() {
    sessions.activateSession(OWNER, MASTER_ID, masterEntity(MASTER_ID))
    val bestia = bestiaOf(MASTER_ID)
    visibility.observers[bestia] = setOf(WATCHER)

    assertEquals(setOf(WATCHER, OWNER), sut.of(world, bestia))
  }

  @Test
  fun `a bestia of the account's other master stays out of sight`() {
    sessions.activateSession(OWNER, MASTER_ID, masterEntity(MASTER_ID))
    val bestia = bestiaOf(OTHER_MASTER_ID)

    assertEquals(emptySet(), sut.of(world, bestia))
  }

  @Test
  fun `a bestia of a player who logged out stays out of sight`() {
    val bestia = bestiaOf(MASTER_ID)

    assertEquals(emptySet(), sut.of(world, bestia))
  }

  @Test
  fun `a mob only reaches those who see it`() {
    val mob = world.createEntity { }
    visibility.observers[mob] = setOf(WATCHER)

    assertEquals(setOf(WATCHER), sut.of(world, mob))
  }

  private val party = setOf(PartyMembership.Member(OWNER, MASTER_ID), PartyMembership.Member(MEMBER, MEMBER_MASTER_ID))

  private fun partyMaster(accountId: Long, masterId: Long): EntityId {
    return world.createEntity { id ->
      add(id, Account(accountId))
      add(id, Master(masterId))
      add(id, PartyMembership(PARTY_ID, party, PartyExpShare.EVEN_SHARE))
    }
  }

  @Test
  fun `a master's vitals reach its owner and every party member being played, wherever they stand`() {
    val owner = partyMaster(OWNER, MASTER_ID)
    sessions.activateSession(OWNER, MASTER_ID, owner)
    sessions.activateSession(MEMBER, MEMBER_MASTER_ID, partyMaster(MEMBER, MEMBER_MASTER_ID))

    assertEquals(setOf(OWNER, MEMBER), sut.ownerAndParty(world, owner))
  }

  /** The party is the master's: the account's other master is not in it. */
  @Test
  fun `a member whose account plays another master is left out`() {
    val owner = partyMaster(OWNER, MASTER_ID)
    sessions.activateSession(OWNER, MASTER_ID, owner)
    sessions.activateSession(MEMBER, OTHER_MASTER_ID, masterEntity(OTHER_MASTER_ID))

    assertEquals(setOf(OWNER), sut.ownerAndParty(world, owner))
  }

  @Test
  fun `the bestia a member plays is heard by the party, one left standing only by its owner`() {
    val owner = partyMaster(OWNER, MASTER_ID)
    sessions.activateSession(OWNER, MASTER_ID, owner)
    sessions.activateSession(MEMBER, MEMBER_MASTER_ID, partyMaster(MEMBER, MEMBER_MASTER_ID))
    val bestia = bestiaOf(MASTER_ID)

    assertEquals(setOf(OWNER), sut.ownerAndParty(world, bestia))

    world.add(bestia, ActivePlayer)

    assertEquals(setOf(OWNER, MEMBER), sut.ownerAndParty(world, bestia))
  }

  @Test
  fun `a mob has no owner and no party to tell`() {
    assertEquals(emptySet(), sut.ownerAndParty(world, world.createEntity { }))
  }

  private companion object {
    const val OWNER = 1L
    const val WATCHER = 2L
    const val MASTER_ID = 7L
    const val OTHER_MASTER_ID = 8L
    const val MEMBER = 3L
    const val MEMBER_MASTER_ID = 9L
    const val PARTY_ID = 5L
  }
}
