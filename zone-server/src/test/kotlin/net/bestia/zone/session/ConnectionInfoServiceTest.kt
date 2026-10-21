package net.bestia.zone.session

import net.bestia.account.Authority
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.session.ConnectionInfoService.PlayerEntity
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionInfoServiceTest {

  private val sut = ConnectionInfoService()

  private val presence = mutableListOf<Triple<Long, Long, Boolean>>()
  private val recording = ConnectionInfoService { event ->
    if (event is MasterPresenceChangedEvent) presence += Triple(event.accountId, event.masterId, event.online)
  }

  @Test
  fun `selecting a master and losing the connection are both told`() {
    recording.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)
    recording.removeSession(ACCOUNT)

    assertEquals(listOf(Triple(ACCOUNT, MASTER, true), Triple(ACCOUNT, MASTER, false)), presence)
  }

  @Test
  fun `a logout is told once, and a repeated one not at all`() {
    recording.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)
    presence.clear()

    recording.deactivateSession(ACCOUNT)
    recording.deactivateSession(ACCOUNT)
    recording.removeSession(ACCOUNT)

    assertEquals(listOf(Triple(ACCOUNT, MASTER, false)), presence)
  }

  /** Re-attaching to the master already selected changes nobody's presence, so nothing is told. */
  @Test
  fun `re-activating the same master is not told again`() {
    recording.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)
    recording.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)

    assertEquals(listOf(Triple(ACCOUNT, MASTER, true)), presence)
  }

  @Test
  fun `switching straight to another master tells both`() {
    recording.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)
    recording.activateSession(ACCOUNT, OTHER_MASTER, OTHER_MASTER_ENTITY)

    assertEquals(
      listOf(Triple(ACCOUNT, MASTER, true), Triple(ACCOUNT, MASTER, false), Triple(ACCOUNT, OTHER_MASTER, true)),
      presence
    )
    assertTrue(recording.isPlaying(ACCOUNT, OTHER_MASTER))
    assertFalse(recording.isPlaying(ACCOUNT, MASTER))
  }

  @Test
  fun `asking about an account that never connected leaves nothing behind`() {
    assertTrue(sut.getOwnedEntitiesByMaster(STRANGER, MASTER).isEmpty())
    assertTrue(sut.getAuthorities(STRANGER).isEmpty())
    assertThrows<NoActiveSessionException> { sut.getMasterId(STRANGER) }
    sut.deactivateSession(STRANGER)

    assertEquals(0, sut.sessionCount)
  }

  @Test
  fun `a disconnect forgets the account`() {
    sut.registerAuthenticatedConnection(ACCOUNT, emptySet())
    sut.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)

    sut.removeSession(ACCOUNT)

    assertEquals(0, sut.sessionCount)
    assertThrows<NoActiveSessionException> { sut.getMasterId(ACCOUNT) }
  }

  @Test
  fun `granted authorities are known before a master is selected, active ones only after`() {
    sut.registerAuthenticatedConnection(ACCOUNT, setOf(Authority.TEST_BESTIA))

    assertEquals(setOf(Authority.TEST_BESTIA), sut.getGrantedAuthorities(ACCOUNT))
    assertTrue(sut.getAuthorities(ACCOUNT).isEmpty())

    sut.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)

    assertEquals(setOf(Authority.TEST_BESTIA), sut.getGrantedAuthorities(ACCOUNT))
    assertEquals(setOf(Authority.TEST_BESTIA), sut.getAuthorities(ACCOUNT))
    assertTrue(sut.getGrantedAuthorities(STRANGER).isEmpty())
  }

  /** The bestias stayed in the world while their owner was away; their component is how they are found. */
  @Test
  fun `a master selected again knows the bestias the world says it owns`() {
    val world = testWorld()
    val bestia = world.createEntity { id -> add(id, OwnedBestia(masterId = MASTER, playerBestiaId = 5L)) }
    world.createEntity { id -> add(id, OwnedBestia(masterId = OTHER_MASTER, playerBestiaId = 6L)) }
    sut.registerAuthenticatedConnection(ACCOUNT, emptySet())
    sut.activateSession(ACCOUNT, MASTER, MASTER_ENTITY)
    sut.removeSession(ACCOUNT)

    sut.registerAuthenticatedConnection(ACCOUNT, emptySet())
    sut.activateSession(ACCOUNT, MASTER, MASTER_ENTITY, ownedBestias = OwnedBestia.ownedBy(world, MASTER))

    assertEquals(setOf(PlayerEntity(5L, bestia)), sut.getOwnedEntitiesByMaster(ACCOUNT, MASTER))
    sut.activateEntity(ACCOUNT, bestia)
    assertEquals(5L, sut.getActivePlayerBestiaId(ACCOUNT))
  }

  private companion object {
    const val ACCOUNT = 1L
    const val STRANGER = 99L
    const val MASTER = 10L
    const val OTHER_MASTER = 11L
    const val MASTER_ENTITY = 1_000L
    const val OTHER_MASTER_ENTITY = 1_001L
  }
}
