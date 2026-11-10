package net.bestia.zone.treasure

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.worldgen.treasure.TreasureCaches
import net.bestia.worldgen.treasure.TreasureTier
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.dialog.DialogArg
import net.bestia.zone.dialog.DialogId
import net.bestia.zone.dialog.DialogService
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreasureSenseTest {

  private val worldService = mockk<WorldService> {
    every { generated } returns world
    every { record } returns mockk(relaxed = true)
  }
  private val registry = TreasureCacheRegistry(worldService, mockk(relaxed = true), mockk(relaxed = true))
  private val sightings = TreasureSightings()
  private val dialogs = mockk<DialogService>(relaxed = true)
  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  private val sut = TreasureSense(
    registry = registry,
    richness = mockk { every { of(any()) } returns TreasureTier.RICH },
    sightings = sightings,
    dialogService = dialogs,
    outMessageProcessor = messages,
  )

  private val cache: TreasureCache by lazy {
    val centre = world.config.widthMetres / 2
    TreasureCaches(world).near(centre, centre, SEARCH_RADIUS).first { it.site < 0 }
  }

  @Test
  fun `a cache in reach shimmers and the popup points at it`() {
    val (dialog, args) = cast(level = 1, at = besideCache(dx = 10))

    assertEquals(DialogId.TREASURE_SENSED, dialog)
    assertEquals(DialogArg.Token("DIRECTION_WEST"), args["heading"])
    assertTrue(args.getValue("count") is DialogArg.Number)
    verify { messages.sendToPlayer(ACCOUNT, match<SMSG> { it is TreasureShimmerSMSG && spotOf(cache) in it.spots }) }
  }

  @Test
  fun `rank three tells the depth and rank five the richness`() {
    assertEquals(DialogId.TREASURE_SENSED_DEPTH, cast(level = 3, at = besideCache(dx = 10)).first)

    val (dialog, args) = cast(level = 5, at = besideCache(dx = 10))
    assertEquals(DialogId.TREASURE_SENSED_RICHNESS, dialog)
    assertEquals(DialogArg.Token("TREASURE_TIER_RICH"), args["richness"])
  }

  @Test
  fun `a cache beyond the radius is not sensed`() {
    val far = besideCache(dx = 30)
    val dialog = cast(level = 1, at = far).first

    val nearer = registry.buriedNear(far.x + 0.5, far.y + 0.5, 20.0)
    assertEquals(if (nearer.isEmpty()) DialogId.TREASURE_NONE else DialogId.TREASURE_SENSED, dialog)
    assertTrue(nearer.none { it.id == cache.id })
  }

  @Test
  fun `what was sensed is remembered for Treasure Map`() {
    cast(level = 1, at = besideCache(dx = 10))

    assertTrue(sightings.recentNear(MASTER, cache.x, cache.y).any { it.id == cache.id })
    assertFalse(sightings.recentNear(MASTER + 1, cache.x, cache.y).any { it.id == cache.id })
  }

  private fun cast(level: Int, at: Vec3L): Pair<DialogId, Map<String, DialogArg>> {
    val dialog = slot<DialogId>()
    val args = slot<Map<String, DialogArg>>()
    every { dialogs.send(any(), capture(dialog), capture(args), any()) } returns Unit

    val battle = BattleContextFixture.entityCtx()
    val skillWorld = RecordingSkillWorld()
      .putPosition(battle.attacker.id, at)
      .putMaster(battle.attacker.id, masterId = MASTER, accountId = ACCOUNT)

    sut.execute(SkillContextFixture.skillCtx(battle, skillWorld, skillLevel = level))

    return dialog.captured to args.captured
  }

  private fun besideCache(dx: Long): Vec3L {
    val spot = spotOf(cache)
    return Vec3L(spot.x + dx, spot.y, spot.z)
  }

  private fun spotOf(cache: TreasureCache): Vec3L {
    return Vec3L(floor(cache.x).toLong(), floor(cache.y).toLong(), cache.floor.roundToLong())
  }

  private companion object {
    const val ACCOUNT = 7L
    const val MASTER = 3L
    const val SEARCH_RADIUS = 3_000.0

    val world: GeneratedWorld
      get() {
        return TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
      }
  }
}
