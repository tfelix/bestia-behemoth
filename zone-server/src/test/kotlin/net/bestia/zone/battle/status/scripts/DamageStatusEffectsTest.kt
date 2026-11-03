package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DamageStatusEffectsTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(
    listOf(Poison(), Bleeding(), Burning(RegionGrid(WaterSource.NONE)), Sick(), Regenerating(), Frozen(CastCancelService()))
  )
  private val service = StatusEffectService(definitions, scripts)
  private val world = testWorld(systems = listOf(StatusEffectDurationSystem(service, definitions, scripts)))

  @Test
  fun `poison drains but never takes the last hit point`() {
    val target = withHealth(current = 1, max = 1000)
    service.applyEffect(world, target, StatusEffectId.POISON, level = 1)

    world.tick(2f)
    world.tick(0f)

    assertEquals(0, staged(target))
  }

  @Test
  fun `poison drains a hundredth of the pool every two seconds`() {
    val target = withHealth(current = 1000, max = 1000)
    service.applyEffect(world, target, StatusEffectId.POISON, level = 1)

    world.tick(2f)
    world.tick(0f)

    assertEquals(10, staged(target))
  }

  @Test
  fun `a bleeding body that stands still loses nothing and closes twice as fast`() {
    val target = withHealth(current = 1000, max = 1000)
    service.applyEffect(world, target, StatusEffectId.BLEEDING, level = 1, durationSeconds = 10.0)

    world.tick(1f)
    world.tick(0f)

    assertEquals(0, staged(target))
    assertEquals(8f, remaining(target, StatusEffectId.BLEEDING))
  }

  @Test
  fun `a bleeding body that walks pays for every tile`() {
    val target = withHealth(current = 1000, max = 1000)
    world.add(target, Path(mutableListOf(Vec3L(1, 0, 0))))
    service.applyEffect(world, target, StatusEffectId.BLEEDING, level = 1)

    world.tick(1f)
    world.tick(0f)

    assertTrue(staged(target) > 0)
  }

  @Test
  fun `any heal closes a bleeding wound`() {
    val target = withHealth(current = 500, max = 1000)
    service.applyEffect(world, target, StatusEffectId.BLEEDING, level = 1)

    Healing.heal(world, target, 10)

    assertFalse(world.getOrThrow(target, StatusEffects::class).hasEffect(StatusEffectId.BLEEDING.id))
  }

  @Test
  fun `a sick body gets half of every heal`() {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 4f)
    Sick().apply(world, 1L, context, level = 1, sourceEntityId = null)
    val target = withHealth(current = 0, max = 1000, values = CombatValues.fromContext(1, BASE, context))

    assertEquals(50, Healing.heal(world, target, 100))
  }

  @Test
  fun `a fire creature burns far less than anything else`() {
    val plain = burning(Element.NORMAL)
    val salamander = burning(Element.FIRE)

    world.tick(1f)
    world.tick(0f)

    assertEquals(20, staged(plain))
    assertTrue(staged(salamander) < staged(plain) / 2)
  }

  private fun burning(element: Element): EntityId {
    val target = withHealth(current = 1000, max = 1000)
    world.add(target, Nature(element, Size.SMALL))
    world.add(target, Position(0, 0, 0))
    service.applyEffect(world, target, StatusEffectId.BURNING, level = 1)

    return target
  }

  @Test
  fun `burning puts out a freeze and the other way round`() {
    val target = withHealth(current = 1000, max = 1000)
    service.applyEffect(world, target, StatusEffectId.FROZEN, level = 1)
    service.applyEffect(world, target, StatusEffectId.BURNING, level = 1)

    assertFalse(hasEffect(target, StatusEffectId.FROZEN))

    service.applyEffect(world, target, StatusEffectId.FROZEN, level = 1)
    assertFalse(hasEffect(target, StatusEffectId.BURNING))
  }

  private fun withHealth(current: Int, max: Int, values: CombatValues = CombatValues.ofBase(1, BASE)): EntityId {
    return world.createEntity { id ->
      add(id, Health(current, max))
      add(id, CombatStats(values))
    }
  }

  private fun staged(target: EntityId): Int {
    return world.get(target, IncomingDamage::class)?.total() ?: 0
  }

  private fun remaining(target: EntityId, effect: StatusEffectId): Float {
    return world.getOrThrow(target, StatusEffects::class).activeEffects.single { it.definitionId == effect.id }.remainingSeconds
  }

  private fun hasEffect(target: EntityId, effect: StatusEffectId): Boolean {
    return world.get(target, StatusEffects::class)?.hasEffect(effect.id) ?: false
  }

  private companion object {
    val BASE = StatusValues(strength = 10, vitality = 10, intelligence = 10, agility = 10, willpower = 10, dexterity = 10)
  }
}
