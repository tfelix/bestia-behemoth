package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ai.profile.Courage
import net.bestia.zone.ai.profile.OrderRule
import net.bestia.zone.ai.profile.OrderRule.Action
import net.bestia.zone.ai.profile.OrderRule.Condition
import net.bestia.zone.ai.profile.OrderRulesConverter
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.care.TestBagCare.BREAD
import net.bestia.zone.item.care.TestBagCare.bagWith
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.QueueEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The "when … then …" lines of a bestia's orders, read from the top, the first that holds deciding. */
class OrderRulesTest {

  private lateinit var ai: AiPipelineFixture

  private val home = Vec3L(0, 0, 0)
  private val afield = Vec3L(15, 0, 0)

  @BeforeEach
  fun setup() {
    ai = AiPipelineFixture()
  }

  private fun owned(
    vararg rules: OrderRule,
    profile: String = "passive_wanderer",
    job: BestiaJob = BestiaJob.REST,
    courage: Courage = Courage.STEADY,
    health: Int = 10,
  ): EntityId {
    val orders = AiConfig(job, courage, rules = rules.toList())
    val id = ai.spawnMob(profile, home, health = health, orders = orders)
    ai.world.add(id, OwnedBestia(masterId = MASTER, playerBestiaId = 1L))
    return id
  }

  private fun breadIn(id: EntityId): Int? = ai.world.getOrThrow(id, Inventory::class).getItem(BREAD.toInt())?.amount

  @Test
  fun `a bestia told to eat early eats where its care line would not`() {
    val bestia = owned(OrderRule(Condition.STAMINA_BELOW, 70, Action.EAT))
    ai.world.add(bestia, Stamina(60, 100))
    ai.world.add(bestia, bagWith(BREAD to 1))

    ai.tickUntil(describe = { "it still holds ${breadIn(bestia)} bread" }) { breadIn(bestia) == null }
  }

  @Test
  fun `the first rule that holds decides`() {
    val bestia = owned(
      OrderRule(Condition.STAMINA_BELOW, 70, Action.REST_AT_HOME),
      OrderRule(Condition.STAMINA_BELOW, 80, Action.EAT),
    )
    ai.world.add(bestia, Stamina(60, 100))
    ai.world.add(bestia, bagWith(BREAD to 1))
    ai.teleport(bestia, afield)

    ai.tickUntilGoal(bestia, "RestAtHome")
    ai.tickUntil(describe = { "it stopped at ${ai.positionOf(bestia)}" }) { ai.positionOf(bestia).distance(home) <= 3 }
    assertEquals(1, breadIn(bestia), "it ate although an earlier rule said to rest")
  }

  @Test
  fun `a brave bestia told to retreat when hurt does`() {
    val bestia = owned(OrderRule(Condition.HEALTH_BELOW, 50, Action.RETREAT), courage = Courage.BRAVE, health = 3)
    ai.teleport(bestia, afield)

    ai.tickUntilGoal(bestia, "Retreat")
  }

  @Test
  fun `a timid bestia told to hold its ground does not flee a stronger foe, one without the rule does`() {
    val holding = owned(OrderRule(Condition.STRONGER_FOE_IN_SIGHT, null, Action.HOLD_GROUND), courage = Courage.TIMID)
    val fleeing = owned(courage = Courage.TIMID)
    for (bestia in listOf(holding, fleeing)) {
      ai.world.add(bestia, Level(10))
      ai.teleport(bestia, afield)
    }
    val stranger = ai.spawnPlayer(afield + Vec3L(3, 0, 0))
    ai.world.add(stranger, Level(10 + BestiaDomain.STRONGER_FOE_LEVEL_LEAD))

    ai.tickUntil(describe = { "the bestia without the rule never fled" }) {
      assertNotEquals("Retreat", ai.goalNameOf(holding), "it fled although told to hold its ground")
      ai.goalNameOf(fleeing) == "Retreat"
    }
    repeat(20 * 5) {
      ai.tick()
      assertNotEquals("Retreat", ai.goalNameOf(holding), "it fled although told to hold its ground")
    }
  }

  @Test
  fun `a bestia told to keep working at night does not go to sleep, one without the rule does`() {
    ai.setNight()
    // A day-active species, which goes to bed at nightfall.
    val working = owned(OrderRule(Condition.NIGHT, null, Action.KEEP_WORKING), profile = "passiv_day_active")
    val sleeping = owned(profile = "passiv_day_active")

    ai.tickUntil(describe = { "the bestia without the rule never slept" }) {
      assertNotEquals("Sleep", ai.goalNameOf(working), "it slept although told to keep working")
      ai.goalNameOf(sleeping) == "Sleep"
    }
    repeat(20 * 5) {
      ai.tick()
      assertNotEquals("Sleep", ai.goalNameOf(working), "it slept although told to keep working")
    }
  }

  @Test
  fun `a crafter told to rest at night puts its station down`() {
    val crafter = owned(OrderRule(Condition.NIGHT, null, Action.REST_AT_HOME), job = BestiaJob.CRAFT)
    ai.structures.add(
      StructureEntry(
        1L, StaticEntityKind.WORKBENCH, MASTER, ownerAccountId = null, Vec3L(6, 0, 0), yaw = 0f,
        queue = listOf(QueueEntry(recipeId = 1L, amount = 2)), takesAnyCrafter = true,
      )
    )
    ai.tickUntil { ai.world.get(crafter, WorkAssignment::class) != null }

    ai.setNight()

    ai.tickUntil { ai.world.get(crafter, WorkAssignment::class) == null }
    repeat(20 * 10) {
      ai.tick()
      assertEquals(null, ai.world.get(crafter, WorkAssignment::class), "it went back to work at night")
    }
  }

  @Test
  fun `a full bag rule hauls before the haul line`() {
    val rules = listOf(OrderRule(Condition.BAG_ABOVE, 30, Action.HAUL_NOW))

    assertTrue(BestiaDomain.ruleSays(WorldState.of(BestiaDomain.RULES to rules, BestiaDomain.BAG_LOAD_PCT to 40), Action.HAUL_NOW))
    assertFalse(BestiaDomain.ruleSays(WorldState.of(BestiaDomain.RULES to rules, BestiaDomain.BAG_LOAD_PCT to 20), Action.HAUL_NOW))
  }

  @Test
  fun `only sensible lines are valid, eight at most`() {
    assertTrue(AiConfig(rules = listOf(OrderRule(Condition.NIGHT, null, Action.REST_AT_HOME))).isValid)
    assertFalse(AiConfig(rules = listOf(OrderRule(Condition.NIGHT, null, Action.HAUL_NOW))).isValid)
    assertFalse(AiConfig(rules = listOf(OrderRule(Condition.STAMINA_BELOW, null, Action.EAT))).isValid)
    assertFalse(AiConfig(rules = listOf(OrderRule(Condition.NIGHT, 30, Action.KEEP_WORKING))).isValid)
    assertFalse(AiConfig(rules = List(9) { OrderRule(Condition.DAY, null, Action.KEEP_WORKING) }).isValid)
  }

  @Test
  fun `rules survive their column, and a line no longer known is dropped`() {
    val converter = OrderRulesConverter()
    val rules = listOf(OrderRule(Condition.STAMINA_BELOW, 30, Action.EAT), OrderRule(Condition.NIGHT, null, Action.REST_AT_HOME))

    assertEquals(rules, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(rules)))
    assertEquals(rules.take(1), converter.convertToEntityAttribute("STAMINA_BELOW:30:EAT;FULL_MOON::HOWL"))
  }

  private companion object {
    const val MASTER = 7L
  }
}
