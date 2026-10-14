package net.bestia.zone.scenarios

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualComponentSMSG
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.master.net.GetSelfCMSG
import net.bestia.zone.movement.ecs.Grounded
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.PositionSMSG
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A player keeps their own master and bestias in sight wherever these stand, so the client map can show them,
 * against the real tick loop and chunk streaming.
 *
 * Messages are kept between tests: [holdTerrain] needs the manifest the connect sent.
 */
class OwnedEntitySyncScenario : BestiaNoSocketScenario(clearMessagesBetweenTests = false) {

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var subscriptions: ChunkSubscriptionService

  @Autowired
  private lateinit var chunkService: ChunkService

  private fun ownBestias(): List<EntityId> {
    val accountId = clientPlayer1.connectedPlayerId
    val masterId = connectionInfoService.getMasterId(accountId)

    return connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId).map { it.entityId }
  }

  private fun moveTo(entityId: EntityId, to: Vec3L) {
    world.modify(entityId) { id ->
      val position = get(id, Position::class)!!
      position.x = to.x
      position.y = to.y
      position.z = to.z
    }
  }

  @Test
  @Order(1)
  fun `getting self introduces the player's bestias wherever they stand, and no other master's`() {
    // The client holds no terrain and has not asked for itself yet, so none of this can have arrived before.
    val ownBestias = ownBestias()
    val otherMastersBestias = world.read { OwnedBestia.ownedBy(this, testData.account1.masterIds[1]) }
      .map { it.entityId }
    assertEquals(2, ownBestias.size)

    clientPlayer1.sendMessage(GetSelfCMSG(clientPlayer1.connectedPlayerId))

    await {
      ownBestias.forEach { bestia ->
        assertTrue(clientPlayer1.receivedAny(VisualComponentSMSG::class) { it.entityId == bestia })
        assertTrue(clientPlayer1.receivedAny(PositionSMSG::class) { it.entityId == bestia })
      }
    }
    otherMastersBestias.forEach { bestia ->
      assertFalse(clientPlayer1.receivedAny(PositionSMSG::class) { it.entityId == bestia })
    }
  }

  @Test
  @Order(2)
  fun `a bestia walking out of view stays in sight for its owner, a stranger does not`() {
    val accountId = clientPlayer1.connectedPlayerId
    val masterPos = assertNotNull(
      world.read { get(connectionInfoService.getActiveEntityId(accountId), Position::class)?.toVec3L() }
    )
    holdTerrain(clientPlayer1)
    val masterChunk = ChunkCoords.chunkOf(chunkService.config, masterPos)
    await { assertTrue(masterChunk in subscriptions.sentTo(accountId)) }

    val chunkSize = chunkService.config.chunkSize
    val inView = masterPos.copy(
      x = masterChunk.x.toLong() * chunkSize + chunkSize / 2,
      y = masterChunk.y.toLong() * chunkSize + chunkSize / 2
    )
    val farAway = inView.copy(x = inView.x + 4000)

    val bestia = ownBestias().first()
    val bestiaHome = world.read { get(bestia, Position::class)!!.toVec3L() }
    val stranger = world.createEntity { id ->
      add(id, Position.fromVec3(inView))
      add(id, Grounded)
      add(id, Speed())
      add(id, EntityVisual(VisualKind.BESTIA, 1L))
    }

    try {
      moveTo(bestia, inView)
      await { assertTrue(clientPlayer1.receivedAny(PositionSMSG::class) { it.entityId == stranger }) }
      clientPlayer1.clearMessages()

      moveTo(bestia, farAway)
      moveTo(stranger, farAway)

      await {
        assertTrue(
          clientPlayer1.receivedAny(VanishEntitySMSG::class) {
            it.entityId == stranger && it.kind == VanishEntitySMSG.VanishKind.OUT_OF_SIGHT
          },
          "a stranger leaving the held terrain leaves the client"
        )
        assertTrue(
          clientPlayer1.receivedAny(PositionSMSG::class) { it.entityId == bestia },
          "the owner's map still needs to know where its bestia went"
        )
      }
      assertFalse(clientPlayer1.receivedAny(VanishEntitySMSG::class) { it.entityId == bestia })
    } finally {
      moveTo(bestia, bestiaHome)
      world.modify(stranger) { id -> destroy(id) }
    }
  }
}
