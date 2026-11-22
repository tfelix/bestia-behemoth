package net.bestia.zone.battle.ammo

import io.mockk.mockk
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.damage.CriticalHit
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.battle.status.scripts.Poison
import net.bestia.zone.battle.status.scripts.Stun
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.crafting.CraftingCatalog
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SpecialArrowsTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(listOf(Poison(), Stun(mockk(relaxed = true))))
  private val world = testWorld()

  /** One roll for hit and crit alike: low enough to land, too high for a plain shot's few points of CRIT. */
  private val strategies = AttackStrategyFactory(LineOfSightService(), FixedRandom(0.15f))

  private fun shot(): EntityBattleContext {
    val ctx = BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack().copy(attackType = AttackType.RANGED_PHYSICAL)
    )
    return ctx as EntityBattleContext
  }

  @Test
  fun `a sharp arrow turns a plain hit into a critical`() {
    val plain = shot()
    val sharp = shot().also { SharpArrow().beforeHit(it) }

    assertIs<HitDamage>(strategies.getAttackStrategy(plain).execute(plain))
    assertIs<CriticalHit>(strategies.getAttackStrategy(sharp).execute(sharp))
  }

  @Test
  fun `a poison arrow poisons and a stun arrow stuns what they hit`() {
    val service = StatusEffectService(definitions, scripts, FixedRandom(0f))
    val target = fighter()

    PoisonArrow(service).onHit(world, fighter(), target, HitDamage(5))
    StunArrow(service).onHit(world, fighter(), target, HitDamage(5))

    val effects = world.getOrThrow(target, StatusEffects::class)
    assertTrue(effects.hasEffect(StatusEffectId.POISON.id))
    assertTrue(effects.hasEffect(StatusEffectId.STUN.id))
  }

  @Test
  fun `a roll above the chance does nothing`() {
    val service = StatusEffectService(definitions, scripts, FixedRandom(0.99f))
    val target = fighter()

    StunArrow(service).onHit(world, fighter(), target, HitDamage(5))

    assertTrue(world.get(target, StatusEffects::class)?.activeEffects.isNullOrEmpty())
  }

  @Test
  fun `every special arrow is ammo, has its element, and its script answers to a bean`() {
    val catalog = CraftingCatalog()
    val registry = AmmoEffectRegistry(listOf(SharpArrow(), PoisonArrow(mockk()), StunArrow(mockk())))
    val expected = mapOf(
      "fire_arrow" to Element.FIRE, "crystal_arrow" to Element.WATER, "wind_arrow" to Element.WIND,
      "stone_arrow" to Element.EARTH, "silver_arrow" to Element.HOLY, "shadow_arrow" to Element.SHADOW,
      "poison_arrow" to Element.POISON, "steel_arrow" to Element.NORMAL, "sharp_arrow" to Element.NORMAL,
      "stun_arrow" to Element.NORMAL,
    )

    for ((name, element) in expected) {
      val item = catalog.item(name)
      assertEquals("AMMO", item.type, name)
      assertEquals(element, item.element, name)
      item.script?.let { assertTrue(registry.effectOf(it) !== AmmoEffect.NONE, "$name: $it") }
    }
  }

  @Test
  fun `each special arrow has a recipe behind the right Arrow Crafting level`() {
    val catalog = CraftingCatalog()
    val arrowCrafting = catalog.skillIdByName.getValue("M_ARROW_CRAFTING")
    val levels = catalog.recipes.forSkill(arrowCrafting).associate { catalog.item(it.output!!.itemId).identifier to it.requiredSkillLevel }

    assertEquals(1, levels["arrow"])
    assertEquals(2, levels["steel_arrow"])
    assertEquals(3, levels["stone_arrow"])
    assertEquals(4, levels["silver_arrow"])
    assertEquals(5, levels["stun_arrow"])
    assertEquals(11, levels.size)
  }

  private fun fighter() = world.createEntity { id -> add(id, CombatStats(CombatValues.ofBase(1, BASE))) }

  private companion object {
    val BASE = StatusValues(strength = 1, vitality = 1, intelligence = 1, agility = 1, willpower = 1, dexterity = 1)
  }
}
