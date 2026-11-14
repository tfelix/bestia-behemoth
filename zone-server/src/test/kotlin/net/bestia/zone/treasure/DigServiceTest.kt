package net.bestia.zone.treasure

import net.bestia.zone.skill.skillIdsOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.worldgen.treasure.TreasureCaches
import net.bestia.worldgen.treasure.TreasureTier
import net.bestia.worldgen.voxel.BlockType
import net.bestia.zone.battle.GearWear
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemIntent
import net.bestia.zone.item.ecs.ObtainItemIntent.CreateItemsIntent
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.treasure.ecs.Digging
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.ChunkStreamInbox
import org.junit.jupiter.api.Test
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DigServiceTest {

  private val worldService = mockk<WorldService> {
    every { generated } returns world
    every { config } returns world.config
    every { isLoaded } returns true
    every { record } returns mockk(relaxed = true)
  }
  private val chunkService = ChunkService(worldService, ChunkStreamConfig())
  private val registry = TreasureCacheRegistry(worldService, mockk(relaxed = true), mockk(relaxed = true))
  private val inbox = ChunkStreamInbox()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  private val service = DigService(
    registry = registry,
    loot = mockk { every { roll(any(), any(), any()) } returns listOf(CreateItemIntent(COIN, 10)) },
    maps = mockk(relaxed = true),
    relics = mockk(relaxed = true),
    richness = mockk { every { of(any()) } returns TreasureTier.POOR },
    chunkService = chunkService,
    inbox = inbox,
    gearWear = GearWear.NONE,
    items = mockk {
      every { idOf("shovel") } returns SHOVEL
      every { idOf("pickaxe") } returns PICKAXE
    },
    skillIds = skillIdsOf(),
    outMessageProcessor = messages,
    pickaxeMining = mockk(relaxed = true),
  )

  private val ecs = testWorld()

  /** A wild cache under soil, so a shovel digs it. */
  private val cache: TreasureCache by lazy {
    val centre = world.config.widthMetres / 2
    TreasureCaches(world).near(centre, centre, SEARCH_RADIUS)
      .filter { it.site < 0 }
      .first { blockAt(Vec3L(floor(it.x).toLong(), floor(it.y).toLong(), floor(it.floor - 0.5).toLong())) in SOIL }
  }

  @Test
  fun `digging over a cache goes on until it opens and gives what it held`() {
    val digger = diggerBeside(cache, tool = SHOVEL)

    service.begin(ecs, digger, ACCOUNT, spotOf(cache))
    val metres = digToTheEnd(digger)

    assertEquals(ceil(cache.depth).toInt(), metres, "one dig per metre down to the cache")
    assertEquals(metres, inbox.drainCarves().size, "every metre is carved")
    assertEquals(listOf(COIN), ecs.get(digger, CreateItemsIntent::class)?.grants?.map { it.itemId })
    assertTrue(registry.buriedNear(cache.x, cache.y, 1.0).none { it.id == cache.id }, "an opened cache is gone")
  }

  @Test
  fun `digging where nothing is buried stops after a metre with nothing found`() {
    val empty = Vec3L(spotOf(cache).x + EMPTY_OFFSET, spotOf(cache).y, spotOf(cache).z)
    assertTrue(registry.buriedNear(empty.x + 0.5, empty.y + 0.5, 3.0).isEmpty(), "the fixture spot holds a cache")
    val digger = createDigger(Vec3L(empty.x + 2, empty.y, empty.z), tool = SHOVEL)

    service.begin(ecs, digger, ACCOUNT, empty)

    assertEquals(1, digToTheEnd(digger))
    verify { messages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.DIG_NOTHING_FOUND)) }
  }

  @Test
  fun `digging needs a tool in the right hand`() {
    val digger = diggerBeside(cache, tool = null)

    service.begin(ecs, digger, ACCOUNT, spotOf(cache))

    assertNull(ecs.get(digger, Digging::class))
    verify { messages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.DIG_NEEDS_TOOL)) }
  }

  @Test
  fun `soil wants a shovel, not a pickaxe`() {
    val digger = diggerBeside(cache, tool = PICKAXE)

    service.begin(ecs, digger, ACCOUNT, spotOf(cache))

    assertNull(ecs.get(digger, Digging::class))
    verify { messages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.DIG_NEEDS_SHOVEL)) }
  }

  @Test
  fun `a spot out of reach is refused`() {
    val far = spotOf(cache)
    val digger = createDigger(Vec3L(far.x + 20, far.y, far.z), tool = SHOVEL)

    service.begin(ecs, digger, ACCOUNT, far)

    assertFalse(ecs.has(digger, Digging::class))
    verify { messages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.DIG_TOO_FAR)) }
  }

  @Test
  fun `the trench climbs out toward the digger`() {
    val digger = diggerBeside(cache, tool = SHOVEL)

    service.begin(ecs, digger, ACCOUNT, spotOf(cache))
    val trench = assertNotNull(ecs.get(digger, Digging::class)).trench

    assertTrue(trench.towardX > 0.9, "the digger stands east of the spot")
    assertEquals(cache.floor, trench.topZ)
  }

  /** Runs each metre to its end, as `DiggingSystem` would, and counts them. */
  private fun digToTheEnd(digger: EntityId): Int {
    var metres = 0
    while (true) {
      val digging = ecs.get(digger, Digging::class) ?: return metres
      ecs.remove(digger, Digging::class)
      service.finish(ecs, digger, digging)
      metres++
    }
  }

  private fun diggerBeside(cache: TreasureCache, tool: Long?): EntityId {
    val spot = spotOf(cache)
    return createDigger(Vec3L(spot.x + 2, spot.y, spot.z), tool)
  }

  private fun createDigger(at: Vec3L, tool: Long?): EntityId {
    return ecs.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Equipment(EquipmentSlots.ALL).also { equipment ->
        if (tool != null) {
          equipment.equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(tool, uniqueId = 1L, durability = 50, maxDurability = 50))
        }
      })
    }
  }

  private fun spotOf(cache: TreasureCache): Vec3L {
    return Vec3L(floor(cache.x).toLong(), floor(cache.y).toLong(), cache.floor.roundToLong())
  }

  private fun blockAt(voxel: Vec3L): BlockType {
    val config = world.config
    val localised = ChunkCoords.localise(config, voxel.x, voxel.y, voxel.z)!!
    val index = ChunkCoords.voxelIndex(config, localised.localX, localised.localY, localised.localZ)
    return BlockType.of(chunkService.merged(chunkService.normalise(localised.chunk)).blocks[index].toInt() and 0xFF)
  }

  private companion object {
    const val ACCOUNT = 7L
    const val COIN = 11L
    const val SHOVEL = 438L
    const val PICKAXE = 57L
    const val SEARCH_RADIUS = 3_000.0
    const val EMPTY_OFFSET = 9L

    val SOIL = setOf(BlockType.DIRT, BlockType.GRASS, BlockType.DRY_GRASS, BlockType.SAND, BlockType.GRAVEL)

    val world: GeneratedWorld
      get() {
        return TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
      }
  }
}
