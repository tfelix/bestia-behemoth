package net.bestia.zone.auction

import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.settlement.TownRegistry

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.StartTownMarkets
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuctionHouseInteractionTest {

  private val accountId = 1L
  private val hallAt = Vec3L(100, 100, 32)

  private lateinit var world: EcsWorld
  private lateinit var messages: OutMessageProcessor
  private lateinit var interaction: AuctionHouseInteraction
  private lateinit var structures: PlayerStructureRegistry
  private lateinit var towns: TownRegistry
  private lateinit var playerHouses: PlayerHouses
  private lateinit var houseService: PlayerHouseService
  private var hall: EntityId = 0L

  @BeforeEach
  fun setUp() {
    world = testWorld()
    messages = mockk(relaxed = true)

    val worldService = mockk<WorldService>()
    every { worldService.config } returns WorldConfig(seed = 1L)

    val markets = mockk<StartTownMarkets>()
    every { markets.townOf(HALL_PROP_ID) } returns "Ashford"
    every { markets.towns() } returns listOf("Ashford", "Brightwater", "Coldmere")

    structures = mockk(relaxed = true)
    towns = mockk(relaxed = true)
    playerHouses = mockk(relaxed = true)
    houseService = mockk(relaxed = true)
    interaction = AuctionHouseInteraction(
      AuctionHouses(worldService, markets, structures, towns, playerHouses), houseService, messages
    )

    // Eight metres along x, six across y.
    hall = world.createEntity { id ->
      add(id, PropPose(hallAt, yaw = 0f))
      add(id, WorldObjectIdentity(HALL_PROP_ID, latticeVersion = 1L))
      add(id, StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 80, halfWidthDm = 60))
    }
  }

  @Test
  fun `standing at the gable is close enough, though the centre is far`() {
    val player = playerAt(Vec3L(hallAt.x + 10, hallAt.y, hallAt.z))

    assertNull(interaction.interact(world, player, hall))
    verify {
      messages.sendToPlayer(
        accountId,
        AuctionHouseOpenSMSG(hall, "Ashford", listOf("Ashford", "Brightwater", "Coldmere"))
      )
    }
  }

  @Test
  fun `standing across the square is refused`() {
    val player = playerAt(Vec3L(hallAt.x, hallAt.y + 10, hallAt.z))

    assertEquals(OpError.AUCTION_OUT_OF_RANGE, interaction.interact(world, player, hall))
    verify(exactly = 0) { messages.sendToPlayer(any(), any<SMSG>()) }
  }

  @Test
  fun `a player house in a standing town opens through its owner's service`() {
    val house = playerHouse()
    every { towns.standingAt(hallAt) } returns mockk { every { name } returns "Oakhold" }
    val player = playerAt(Vec3L(hallAt.x + 10, hallAt.y, hallAt.z))

    assertNull(interaction.interact(world, player, house))
    verify { houseService.open(accountId, house, AuctionHouses.At.Player(OWNED, "Oakhold")) }
  }

  @Test
  fun `a player house whose town fell is closed`() {
    val house = playerHouse()
    every { towns.standingAt(hallAt) } returns null
    val player = playerAt(Vec3L(hallAt.x + 10, hallAt.y, hallAt.z))

    assertEquals(OpError.AUCTION_HOUSE_CLOSED, interaction.interact(world, player, house))
  }

  private fun playerHouse(): EntityId {
    every { playerHouses.of(OWNED.structureId) } returns OWNED
    every { structures.of(OWNED.structureId) } returns StructureEntry(OWNED.structureId, StaticEntityKind.AUCTION_HOUSE, 3, 7, hallAt, 0f)

    return world.createEntity { id ->
      add(id, PropPose(hallAt, yaw = 0f))
      add(id, PlayerStructureIdentity(OWNED.structureId, 7L))
      add(id, StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 80, halfWidthDm = 60))
    }
  }

  private fun playerAt(at: Vec3L): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Account(accountId))
    }
  }

  private companion object {
    val OWNED = PlayerHouses.House(42L, 3L, 7L, "Ada", feePercent = 5, feeChangedAt = null)

    const val HALL_PROP_ID = 4242L
  }
}
