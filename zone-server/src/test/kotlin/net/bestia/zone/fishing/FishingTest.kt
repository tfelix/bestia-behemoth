package net.bestia.zone.fishing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.battle.GearWear
import net.bestia.zone.battle.ecs.attack.AttackCancelService
import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.fishing.ecs.Fishing
import net.bestia.zone.fishing.ecs.FishingSystem
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.navigation.region.WaterSurvey
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.skillIdsOf
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Casting, waiting, striking and reeling in, against the shipped `fishing.yml` and `items.yml`. */
class FishingTest {

  private val gearWear = mockk<GearWear>(relaxed = true)
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val catalogue = shippedCatalogue()

  private var nowNanos = 0L

  /** Always the bottom of the range: the shortest wait, and the first entry the draw can reach. */
  private val lowestRolls = object : Random() {
    override fun nextDouble(): Double {
      return 0.0
    }

    override fun nextFloat(): Float {
      return 0f
    }
  }

  private val service = FishingService(
    catalogue, RegionGrid(WATER_FROM_X_16), gearWear, CastCancelService(), AttackCancelService(),
    StrikeTimingCheck(), lowestRolls, { nowNanos },
  )

  private lateinit var world: EcsWorld

  @BeforeEach
  fun setUp() {
    world = testWorld(systems = listOf(FishingSystem(catalogue, out) { nowNanos }))
  }

  @Test
  fun `a cast onto water in reach puts a line out and wears the rod`() {
    val angler = angler()

    assertNull(service.cast(world, angler, WATER))

    assertEquals(WATER, world.get(angler, Fishing::class)?.bobber)
    verify { gearWear.wearWeapon(world, angler, 1) }
  }

  @Test
  fun `a cast onto dry ground is refused`() {
    val angler = angler()

    assertEquals(OpError.FISHING_NOT_WATER, service.cast(world, angler, Vec3L(15, 0, 0)))
    assertFalse(world.has(angler, Fishing::class))
  }

  @Test
  fun `a cast beyond the line's reach is refused`() {
    assertEquals(OpError.FISHING_TOO_FAR, service.cast(world, angler(), Vec3L(30, 0, 0)))
  }

  @Test
  fun `a cast without a rod in hand is refused`() {
    assertEquals(OpError.REQUEST_REFUSED, service.cast(world, angler(rodItemId = null), WATER))
  }

  @Test
  fun `a strike before the bite pulls the line in empty`() {
    val angler = castLine()

    service.strike(world, angler)

    assertFalse(world.has(angler, Fishing::class))
    assertFalse(world.has(angler, ObtainItemIntent.CreateItemsIntent::class))
  }

  @Test
  fun `a strike during the bite lands a catch and gathering experience`() {
    val angler = castLine()
    waitForBite(angler)

    service.strike(world, angler)

    val grant = world.get(angler, ObtainItemIntent.CreateItemsIntent::class)?.grants?.single()
    assertEquals(itemId("old_boot"), grant?.itemId)
    // 2 * catch level + 5, for a level 1 boot.
    assertEquals(7, world.get(angler, GainExp::class)?.value)
  }

  @Test
  fun `a bite left alone gets away`() {
    val angler = castLine()
    waitForBite(angler)

    world.tick(catalogue.rules.strikeWindowSeconds + 0.1f)

    assertFalse(world.has(angler, Fishing::class))
    verify { out.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.FISHING_GOT_AWAY)) }
  }

  @Test
  fun `walking away reels the line in`() {
    val angler = castLine()

    world.get(angler, Position::class)!!.stepTo(11, 0, 0)
    world.tick(0.05f)

    assertFalse(world.has(angler, Fishing::class))
  }

  @Test
  fun `the Fishing rank raises the catch level cap`() {
    assertEquals(10, catalogue.catchLevelCap(0))
    assertEquals(20, catalogue.catchLevelCap(1))
    assertEquals(100, catalogue.catchLevelCap(5))
  }

  @Test
  fun `every rod reaches further than the one before`() {
    val reaches = listOf("willow_rod", "bamboo_rod", "ash_rod", "steel_rod").map { catalogue.maxCatchLevelOf(itemId(it)) }

    assertEquals(listOf(25, 50, 75, 120), reaches)
  }

  private fun castLine(): EntityId {
    val angler = angler()
    assertNull(service.cast(world, angler, WATER))
    return angler
  }

  private fun waitForBite(angler: EntityId) {
    world.tick(catalogue.rules.minWaitSeconds + 0.01f)
    assertTrue(world.get(angler, Fishing::class)?.isBiting == true)
  }

  private fun angler(rodItemId: Long? = itemId("willow_rod")): EntityId {
    return world.createEntity { id ->
      add(id, Master(masterId = MASTER_ID))
      add(id, Account(accountId = ACCOUNT_ID))
      add(id, Position.fromVec3(Vec3L(10, 0, 0)))
      add(id, KnownSkills(mutableMapOf(FISHING_SKILL_ID to 1)))
      add(id, Equipment(availableSlotMask = EquipmentSlots.ALL).apply {
        if (rodItemId != null) {
          equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(rodItemId, ROD_UNIQUE_ID, durability = 60, maxDurability = 60))
        }
      })
    }
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 2L
    const val ROD_UNIQUE_ID = 10L
    const val FISHING_SKILL_ID = 30L
    val WATER = Vec3L(16, 0, 0)

    val WATER_FROM_X_16 = object : WaterSource {
      override val isReady = true
      override fun survey(area: Aabb, exact: Boolean): WaterSurvey {
        return WaterSurvey({ x, _ -> x >= 16.0 }, emptyList())
      }
    }

    /** Every item id and base level in the shipped `items.yml`, keyed by name. */
    val ITEMS: Map<String, Pair<Long, Int>> by lazy {
      val tree = ClassPathResource("items.yml").inputStream.use { ObjectMapper(YAMLFactory()).readTree(it) }
      tree["items"].associate {
        it["item-db-name"].asText() to (it["id"].asLong() to (it["base-level"]?.asInt() ?: 1))
      }
    }

    fun itemId(name: String): Long {
      return ITEMS.getValue(name).first
    }

    fun shippedCatalogue(): FishingCatalogue {
      val items = mockk<ItemTemplateRegistry> {
        every { idOf(any()) } answers { ITEMS[firstArg()]?.first }
        every { baseLevelOf(any()) } answers { ITEMS.values.firstOrNull { it.first == firstArg<Long>() }?.second }
      }
      return FishingCatalogue(items, skillIdsOf(SkillId.M_FISHING to FISHING_SKILL_ID)).also {
        it.load()
        it.warmUp()
      }
    }
  }
}
