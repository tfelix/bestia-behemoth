package net.bestia.zone.scenarios

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.movement.ecs.PositionSMSG
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkManifestSMSG
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import net.bestia.zone.world.stream.RemoteViewpoints
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A client looks at a far place while its body stays put: it is offered the terrain there and sees who stands on it. */
class RemoteViewpointScenario : BestiaNoSocketScenario(autoClientConnect = false, clearMessagesBetweenTests = false) {

  @Autowired
  private lateinit var viewpoints: RemoteViewpoints

  @Autowired
  private lateinit var subscriptions: ChunkSubscriptionService

  @Autowired
  private lateinit var chunkService: ChunkService

  @Autowired
  private lateinit var connections: ConnectionInfoService

  @Autowired
  private lateinit var spawner: BestiaEntitySpawner

  @Autowired
  private lateinit var world: WorldView

  private lateinit var farSpot: Vec3L
  private lateinit var bodyGround: ChunkPos
  private var farMob: EntityId? = null

  private val account get() = clientPlayer1.connectedPlayerId

  @AfterAll
  fun leaveNothingBehind() {
    world.read { viewpoints.close(account) }
    farMob?.let { mob -> world.modify(mob) { destroy(it) } }
  }

  @Test
  @Order(1)
  fun `a viewpoint offers the terrain around the far spot`() {
    clientPlayer1.connect(testData.account1.masterIds.first())
    holdTerrain(clientPlayer1)

    val body = world.read { getOrThrow(connections.getActiveEntityId(account), Position::class).toVec3L() }
    val chunkWidth = chunkService.config.chunkSize / ChunkCoords.VOXELS_PER_POSITION_UNIT
    farSpot = body + Vec3L(FAR_CHUNKS * chunkWidth, 0, 0)
    bodyGround = groundOf(body)
    val farGround = groundOf(farSpot)

    world.read { viewpoints.open(account, farSpot, radiusChunks = 1) }

    await { assertTrue(farGround in subscriptions.sentTo(account), "the far ground goes out unasked, as the body's does") }
    assertTrue(bodyGround in subscriptions.sentTo(account), "the body's view stays held")
  }

  @Test
  @Order(2)
  fun `a creature at the far spot is seen`() {
    val mob = world.read { spawner.spawnMob(this, BLOB, farSpot, persistent = false) }
    farMob = mob

    await { assertNotNull(clientPlayer1.tryGetLastReceivedFor(PositionSMSG::class, mob)) }
  }

  @Test
  @Order(3)
  fun `closing the viewpoint withdraws the far terrain and its creatures`() {
    val farGround = groundOf(farSpot)
    clientPlayer1.clearMessages()

    world.read { viewpoints.close(account) }

    await {
      assertTrue(clientPlayer1.receivedAny(ChunkManifestSMSG::class) { farGround in it.removed })
      assertTrue(clientPlayer1.receivedAny(VanishEntitySMSG::class) { it.entityId == farMob })
    }
    assertFalse(farGround in subscriptions.sentTo(account))
    assertTrue(bodyGround in subscriptions.sentTo(account), "only the far view is withdrawn")
  }

  private fun groundOf(at: Vec3L): ChunkPos {
    return chunkService.normalise(ChunkCoords.chunkOf(chunkService.config, at))
  }

  private companion object {
    /** Past the body's view and its release margin, so the two views share no column. */
    const val FAR_CHUNKS = 15L
    const val BLOB = 1L
  }
}
