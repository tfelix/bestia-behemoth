package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatStatusEffectsTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(
    listOf(Enraged(), Fatigue(), Barrier(), Endowed(), Quagmired(), Tailwind(), Berserk())
  )
  private val service = StatusEffectService(definitions, scripts)
  private val world = testWorld()

  @Test
  fun `a rage that ends leaves its bearer fatigued`() {
    val target = world.createEntity { }
    service.applyEffect(world, target, StatusEffectId.ENRAGED, level = 1)

    service.removeEffect(world, target, StatusEffectId.ENRAGED.id, RemovalReason.EXPIRED)

    assertTrue(hasEffect(target, StatusEffectId.FATIGUE))
  }

  @Test
  fun `a barrier soaks blows from its pool and breaks when it runs dry`() {
    // Level 5: 25% of 1000 health, at most five hits.
    val target = world.createEntity { id -> add(id, Health(1000, 1000)) }
    service.applyEffect(world, target, StatusEffectId.BARRIER, level = 5)

    assertEquals(0, service.onDamageTaken(world, target, 200))
    assertEquals(150, service.onDamageTaken(world, target, 200))

    assertFalse(hasEffect(target, StatusEffectId.BARRIER))
  }

  @Test
  fun `a barrier lets no more blows through it than it may take`() {
    val target = world.createEntity { id -> add(id, Health(100_000, 100_000)) }
    service.applyEffect(world, target, StatusEffectId.BARRIER, level = 1)

    repeat(3) { assertEquals(0, service.onDamageTaken(world, target, 1)) }

    assertFalse(hasEffect(target, StatusEffectId.BARRIER))
  }

  @Test
  fun `an endowed weapon hits with the element it was given`() {
    val target = world.createEntity { }
    service.applyEffect(world, target, StatusEffectId.ENDOWED, level = 1, amount = Element.HOLY.ordinal)
    val context = StatusValueRecalcContext(BASE, baseSpeed = 4f)

    Endowed().apply(world, target, context, world.getOrThrow(target, StatusEffects::class).activeEffects.single())

    assertEquals(Element.HOLY, context.weaponElement)
  }

  @Test
  fun `a quagmire strips haste`() {
    val target = world.createEntity { }
    service.applyEffect(world, target, StatusEffectId.TAILWIND, level = 1)

    service.applyEffect(world, target, StatusEffectId.QUAGMIRED, level = 1)

    assertFalse(hasEffect(target, StatusEffectId.TAILWIND))
  }

  @Test
  fun `berserk drops all armour and physical defence to nothing and raises the attack`() {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 4f)
    context.addDefense(hardDefense = 40, hardMagicDefense = 15)
    Berserk().apply(world, 1L, context, level = 1, sourceEntityId = null)

    val values = CombatValues.fromContext(level = 10, base = BASE, context = context)

    assertEquals(0, values.defense.hardDefense)
    assertEquals(0, values.defense.hardMagicDefense)
    assertEquals(0, values.defense.defense, "soft DEF")
    assertEquals(0, values.defense.defenseSpread, "soft DEF")
    assertTrue(values.derived.atk > CombatValues.ofBase(10, BASE).derived.atk)
  }

  private fun hasEffect(target: EntityId, effect: StatusEffectId): Boolean {
    return world.get(target, StatusEffects::class)?.hasEffect(effect.id) ?: false
  }

  private companion object {
    val BASE = StatusValues(strength = 30, vitality = 30, intelligence = 30, agility = 30, willpower = 30, dexterity = 30)
  }
}
