package net.bestia.zone.treasure

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.worldgen.treasure.TreasureTier
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.treasure.persistence.TreasureMapRecord
import org.junit.jupiter.api.Test
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreasureMapTest {

  private val cache = TreasureCache(
    id = 42L, x = 100.0, y = 200.0, floor = 10.0, depth = 2.0, minTier = TreasureTier.POOR, artifact = -1, site = -1
  )
  private val sightings = TreasureSightings()
  private val maps = mockk<TreasureMapService>(relaxed = true)
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val caches = mockk<TreasureCacheRegistry> { every { progressOf(any()) } returns null }

  private val sut = TreasureMap(sightings, caches, maps, messages)

  @Test
  fun `a sensed cache is charted inside a circle that shrinks with rank`() {
    sightings.record(MASTER, listOf(cache))

    for ((level, radius) in listOf(1 to 50.0, 2 to 25.0, 3 to 0.0)) {
      val x = slot<Double>()
      val y = slot<Double>()
      cast(level)

      verify { maps.draw(any(), MASTER, ACCOUNT, cache, capture(x), capture(y), radius) }
      assertTrue(hypot(x.captured - cache.x, y.captured - cache.y) <= radius + 1e-9, "the cache lies outside the circle")
    }
  }

  @Test
  fun `nothing sensed lately is refused`() {
    cast(level = 1)

    verify { messages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.TREASURE_MAP_NOTHING_SENSED)) }
    verify(exactly = 0) { maps.draw(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `every map of a dug cache crumbles in the bags that hold it`() {
    val registry = TreasureMapRegistry(mockk(relaxed = true))
    registry.put(TreasureMapRecord(itemInstanceId = 7L, cacheId = cache.id, centreX = 0.0, centreY = 0.0, radius = 25.0))
    val service = TreasureMapService(
      minter = mockk(relaxed = true),
      registry = registry,
      items = mockk {
        every { idOf("crumbled_treasure_map") } returns CRUMBLED
        every { weightOf(CRUMBLED) } returns 1
      },
      asyncJobExecutor = mockk<AsyncJobExecutor>(relaxed = true),
      outMessageProcessor = messages,
      world = mockk(relaxed = true),
    )
    val world = testWorld()
    val holder = world.createEntity { id ->
      add(id, Inventory(mutableListOf(Inventory.Item(MAP, 1, uniqueId = 7L, stackable = false))))
    }

    service.crumble(world, cache.id)

    assertEquals(listOf(CRUMBLED), world.get(holder, Inventory::class)!!.getItems().map { it.itemId })
    assertEquals(null, registry.of(7L), "a crumbled map draws no circle")
  }

  private fun cast(level: Int) {
    val battle = BattleContextFixture.entityCtx()
    val skillWorld = RecordingSkillWorld()
      .putPosition(battle.attacker.id, Vec3L(110, 200, 10))
      .putMaster(battle.attacker.id, masterId = MASTER, accountId = ACCOUNT)

    sut.execute(SkillContextFixture.skillCtx(battle, skillWorld, skillLevel = level))
  }

  private companion object {
    const val MASTER = 3L
    const val ACCOUNT = 7L
    const val MAP = 128L
    const val CRUMBLED = 129L
  }
}
