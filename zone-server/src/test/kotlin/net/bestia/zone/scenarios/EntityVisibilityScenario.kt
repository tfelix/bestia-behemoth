package net.bestia.zone.scenarios

import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkManifestSMSG
import net.bestia.zone.world.stream.ChunkRequestCMSG
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkSubscriptionService
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.entity.VisualComponentSMSG
import net.bestia.zone.ecs.entity.VisualKind
import net.bestia.zone.ecs.movement.Grounded
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.PositionSMSG
import net.bestia.zone.ecs.movement.Speed
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That an entity is announced to a client because that client holds the ground it stands on, against the real
 * Spring context and the real tick loop.
 *
 * The unit tests prove the pieces - the residency index tracks chunk crossings, the snapshot builder picks and
 * orders the components. This proves the chain: an entity that nothing has marked dirty still reaches a client
 * whose chunk subscription covers it, which is the whole point and is exactly what a radius broadcast over
 * dirty components could never do.
 */
class EntityVisibilityScenario : BestiaNoSocketScenario(clearMessagesBetweenTests = false) {

  private companion object {
    /** Enough passes to walk the request budget through a view volume; the loop stops early once served. */
    const val REQUEST_ROUNDS = 40
  }

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var subscriptions: ChunkSubscriptionService

  @Autowired
  private lateinit var chunkService: ChunkService

  /**
   * Gets terrain into the client's hands, because that is what visibility is now keyed on: an entity is
   * announced to an account that holds the chunk it stands in, and nothing is held until the client has asked
   * for what the manifest offered and been served it.
   */
  private fun holdTerrain(client: GameClientMock) {
    await {
      assertTrue(
        client.tryGetLastReceived(ChunkManifestSMSG::class) != null,
        "the manifest is what authorises a request"
      )
    }

    repeat(REQUEST_ROUNDS) {
      val offered = client.getLastReceived(ChunkManifestSMSG::class).added.map { it.chunk }
      if (offered.isEmpty()) return@repeat

      client.sendMessage(ChunkRequestCMSG(client.connectedPlayerId, offered))
      await { assertTrue(subscriptions.sentTo(client.connectedPlayerId).isNotEmpty()) }
    }
  }

  @Test
  @Order(1)
  fun `an entity walking into view is announced in full, not just its position`() {
    val playerPos = assertNotNull(
      world.read {
        get(connectionInfoService.getActiveEntityId(clientPlayer1.connectedPlayerId), Position::class)
          ?.toVec3L()
      },
      "the selected master has to be in the world before anything can walk up to it"
    )

    holdTerrain(clientPlayer1)

    val playerChunk = ChunkCoords.chunkOf(chunkService.config, playerPos)
    await {
      assertTrue(
        playerChunk in subscriptions.sentTo(clientPlayer1.connectedPlayerId),
        "the player has to be holding the ground it stands on before anything near it can be announced"
      )
    }

    // Inside the player's own chunk by construction rather than "a couple of tiles away": the neighbouring
    // chunk may not have been served yet - chunksPerTickPerPlayer meters that - and a target two tiles east
    // can be over a chunk boundary anyway.
    val chunkSize = chunkService.config.chunkSize
    val insidePlayerChunk = playerPos.copy(
      x = playerChunk.x.toLong() * chunkSize + chunkSize / 2,
      y = playerChunk.y.toLong() * chunkSize + chunkSize / 2
    )

    // Created far outside the interest range, so its spawn flush reaches nobody, and left alone until its
    // components have stopped being dirty. That is the state the old delivery could not recover from.
    val wanderer = world.createEntity { id ->
      add(id, Position.fromVec3(playerPos.copy(x = playerPos.x + 4000)))
      // Grounded, or ChunkStreamSystem snaps it onto the surface where it starts and then leaves it at that
      // elevation once moved - which puts it in a different vertical slab from the player and so, correctly,
      // out of their view.
      add(id, Grounded)
      add(id, Speed())
      add(id, EntityVisual(VisualKind.BESTIA, 1L))
    }

    await {
      assertFalse(
        world.read { get(wanderer, Position::class)!!.isDirty() },
        "wait out the spawn flush, so what arrives below cannot be it"
      )
    }
    clientPlayer1.clearMessages()

    world.modify(wanderer) { id ->
      val position = get(id, Position::class)!!
      position.x = insidePlayerChunk.x
      position.y = insidePlayerChunk.y
    }

    await {
      // The discriminating assertion. Moving into range dirties Position and nothing else, so a delivery
      // driven by dirty components alone can only ever have sent the position - which is why an entity that
      // wandered up to you used to arrive as an invisible node.
      assertTrue(
        clientPlayer1.receivedAny(VisualComponentSMSG::class) { it.entityId == wanderer },
        "coming into view has to carry the appearance, not only the position"
      )
      assertTrue(
        clientPlayer1.receivedAny(PositionSMSG::class) { it.entityId == wanderer }
      )
    }
  }
}
