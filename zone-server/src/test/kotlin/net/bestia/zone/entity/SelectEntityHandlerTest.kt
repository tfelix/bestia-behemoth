package net.bestia.zone.entity

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ai.ecs.PlayerControlled
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectEntityHandlerTest {

  private val world: World = testWorld()
  private val sessions = ConnectionInfoService()
  private val playerAoi = ActivePlayerAOIService()
  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val sut = SelectEntityHandler(sessions, world, mockk(relaxed = true), playerAoi, out)

  private var master: EntityId = 0
  private var bestia: EntityId = 0

  @BeforeEach
  fun setUp() {
    master = world.createEntity { id ->
      add(id, Position.fromVec3(MASTER_AT))
      add(id, ActivePlayer)
    }
    bestia = world.createEntity { id -> add(id, Position.fromVec3(BESTIA_AT)) }

    sessions.activateSession(ACCOUNT_ID, MASTER_ID, master)
    sessions.registerPlayerBestiaEntity(ACCOUNT_ID, MASTER_ID, PLAYER_BESTIA_ID, bestia)
    playerAoi.setEntityPosition(ACCOUNT_ID, MASTER_AT)
  }

  @Test
  fun `selecting an owned bestia moves control and the view onto it`() {
    assertTrue(sut.handle(SelectEntityCMSG(ACCOUNT_ID, bestia)))

    assertEquals(bestia, sessions.getActiveEntityId(ACCOUNT_ID))
    assertTrue(world.has(bestia, ActivePlayer::class))
    assertTrue(world.has(bestia, PlayerControlled::class))
    assertFalse(world.has(master, ActivePlayer::class))
    assertEquals(setOf(ACCOUNT_ID), playerAoi.queryEntitiesInCube(BESTIA_AT, 2))
    verify { out.sendToPlayer(ACCOUNT_ID, ActiveEntitySMSG(bestia)) }
  }

  @Test
  fun `the master can be selected again`() {
    sut.handle(SelectEntityCMSG(ACCOUNT_ID, bestia))

    assertTrue(sut.handle(SelectEntityCMSG(ACCOUNT_ID, master)))

    assertEquals(master, sessions.getActiveEntityId(ACCOUNT_ID))
    assertTrue(world.has(master, ActivePlayer::class))
    assertFalse(world.has(bestia, ActivePlayer::class))
    assertFalse(world.has(bestia, PlayerControlled::class))
  }

  @Test
  fun `an entity the player does not own is refused`() {
    val stranger = world.createEntity { id -> add(id, Position.fromVec3(BESTIA_AT)) }

    assertFalse(sut.handle(SelectEntityCMSG(ACCOUNT_ID, stranger)))

    assertEquals(master, sessions.getActiveEntityId(ACCOUNT_ID))
    assertTrue(world.has(master, ActivePlayer::class))
  }

  private companion object {
    const val ACCOUNT_ID = 3L
    const val MASTER_ID = 7L
    const val PLAYER_BESTIA_ID = 21L
    val MASTER_AT = Vec3L(0, 0, 0)
    val BESTIA_AT = Vec3L(500, 500, 0)
  }
}
