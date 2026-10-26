package net.bestia.zone.auction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuctionHouseInteractionTest {

  private val accountId = 1L
  private val hallAt = Vec3L(100, 100, 32)

  private lateinit var world: EcsWorld
  private lateinit var dialogs: DialogService
  private lateinit var interaction: AuctionHouseInteraction
  private var hall: EntityId = 0L

  @BeforeEach
  fun setUp() {
    world = testWorld()
    dialogs = mockk(relaxed = true)

    val worldService = mockk<WorldService>()
    every { worldService.config } returns WorldConfig(seed = 1L)

    interaction = AuctionHouseInteraction(worldService, dialogs)

    // Eight metres along x, six across y.
    hall = world.createEntity { id ->
      add(id, PropPose(hallAt, yaw = 0f))
      add(id, StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 80, halfWidthDm = 60))
    }
  }

  @Test
  fun `standing at the gable is close enough, though the centre is far`() {
    val player = playerAt(Vec3L(hallAt.x + 10, hallAt.y, hallAt.z))

    assertNull(interaction.interact(world, player, hall))
    verify { dialogs.send(accountId, DialogId.AUCTION_HOUSE_PLACEHOLDER, any(), hall) }
  }

  @Test
  fun `standing across the square is refused`() {
    val player = playerAt(Vec3L(hallAt.x, hallAt.y + 10, hallAt.z))

    assertEquals(OpError.AUCTION_OUT_OF_RANGE, interaction.interact(world, player, hall))
    verify(exactly = 0) { dialogs.send(any(), any<DialogId>(), any(), any()) }
  }

  private fun playerAt(at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Account(accountId))
    }
  }
}
