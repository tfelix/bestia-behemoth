package net.bestia.zone.session

import net.bestia.zone.ecs.account.OwnedBestia
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.session.ConnectionInfoService.PlayerEntity
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionInfoServiceTest {

  private val sut = ConnectionInfoService()

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
  }
}
