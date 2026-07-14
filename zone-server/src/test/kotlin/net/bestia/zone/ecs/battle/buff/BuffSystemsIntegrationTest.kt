package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.battle.BuffService
import net.bestia.zone.battle.buff.BuffDefinition
import net.bestia.zone.battle.buff.BuffDefinitionRegistry
import net.bestia.zone.battle.buff.BuffEffect
import net.bestia.zone.battle.buff.BuffPolarity
import net.bestia.zone.battle.buff.BuffTriggerAction
import net.bestia.zone.battle.buff.BuffTriggerEvent
import net.bestia.zone.battle.buff.ModifierMode
import net.bestia.zone.battle.buff.StackBehavior
import net.bestia.zone.battle.status.StatType
import net.bestia.zone.ecs.battle.Damage
import net.bestia.zone.ecs.battle.ReceivedDamageSystem
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Speed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the buff systems wired together against a real [World], the same way
 * [net.bestia.zone.ecs.EcsConfiguration] wires them in production (minus Spring) - to verify
 * the cross-system ordering and tick-lag behavior called out in the design plan, not just each
 * component in isolation.
 */
class BuffSystemsIntegrationTest {

  private val speedBuff = BuffDefinition(
    id = 1L,
    identifier = "SWIFTNESS",
    polarity = BuffPolarity.BUFF,
    showIcon = true,
    baseDurationSeconds = 1.0,
    effects = listOf(BuffEffect.StatModifierEffect(StatType.SPEED, ModifierMode.MULTIPLICATIVE, 0.5))
  )

  private val reflectBuff = BuffDefinition(
    id = 2L,
    identifier = "THORNS",
    polarity = BuffPolarity.BUFF,
    showIcon = true,
    baseDurationSeconds = 10.0,
    stackBehavior = StackBehavior.IGNORE_IF_PRESENT,
    effects = listOf(
      BuffEffect.TriggerEffect(
        on = BuffTriggerEvent.ON_DAMAGE_TAKEN,
        action = BuffTriggerAction.ReflectDamage(percent = 0.5),
        consumeOnTrigger = true
      )
    )
  )

  private val persistentReflectBuff = BuffDefinition(
    id = 3L,
    identifier = "PERSISTENT_THORNS",
    polarity = BuffPolarity.BUFF,
    showIcon = true,
    baseDurationSeconds = 100.0,
    stackBehavior = StackBehavior.IGNORE_IF_PRESENT,
    effects = listOf(
      BuffEffect.TriggerEffect(
        on = BuffTriggerEvent.ON_DAMAGE_TAKEN,
        action = BuffTriggerAction.ReflectDamage(percent = 1.0),
        consumeOnTrigger = false
      )
    )
  )

  private fun newWorld(): Pair<World, BuffDefinitionRegistry> {
    val registry = BuffDefinitionRegistry()
    registry.load(listOf(speedBuff, reflectBuff, persistentReflectBuff))

    val world = World()
    world.addSystems(
      listOf(
        BuffDamageInterceptSystem(registry),
        BuffDurationSystem(),
        StatAggregationSystem(registry),
        SpeedModifierSystem(),
        ReceivedDamageSystem()
      )
    )
    return world to registry
  }

  @Test
  fun `a speed buff raises effective speed and reverts once it expires`() {
    val (world, registry) = newWorld()
    val entity = world.create()
    world.add(entity, Speed(2.0f))

    BuffService(registry).applyBuff(world, entity, definitionId = speedBuff.id, level = 1)

    world.tick(0.1f)
    assertEquals(3.0f, world.get(entity, Speed::class)!!.speed, 0.001f)

    world.tick(1.0f) // fully expires the 1s duration
    assertEquals(2.0f, world.get(entity, Speed::class)!!.speed, 0.001f)
    assertTrue(world.get(entity, Buffs::class)!!.activeBuffs.isEmpty())
  }

  @Test
  fun `a reflect buff mitigates incoming damage, is consumed, and reflects onto the attacker next tick`() {
    val (world, _) = newWorld()
    val attacker = world.create()
    val target = world.create()

    world.add(attacker, Health(100, 100))
    world.add(target, Health(100, 100))

    world.update(target, default = { Buffs() }) {
      it.applyBuff(reflectBuff, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    }

    world.modify(target) { id ->
      val damage = get(id, Damage::class) ?: add(id, Damage())
      damage.add(20, attacker)
    }

    world.tick(0.1f)

    // target only takes the non-reflected half, in the same tick as ReceivedDamageSystem
    assertEquals(90, world.get(target, Health::class)!!.current)
    // the buff triggered once and was consumed
    assertTrue(world.get(target, Buffs::class)!!.activeBuffs.isEmpty())
    // reflected damage hasn't landed yet - documented one-tick lag (World.add defers while iterating)
    assertEquals(100, world.get(attacker, Health::class)!!.current)

    world.tick(0.1f)

    // now the reflected damage lands on the attacker
    assertEquals(90, world.get(attacker, Health::class)!!.current)
  }

  @Test
  fun `reflected damage does not itself get reflected again`() {
    val (world, registry) = newWorld()
    val a = world.create()
    val b = world.create()

    world.add(a, Health(100, 100))
    world.add(b, Health(100, 100))

    // both sides carry a persistent (non-consuming) reflect buff to stress-test the loop guard
    world.update(a, default = { Buffs() }) {
      it.applyBuff(registry.getOrThrow(3L), level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 100.0)
    }
    world.update(b, default = { Buffs() }) {
      it.applyBuff(registry.getOrThrow(3L), level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 100.0)
    }

    world.modify(b) { id ->
      val damage = get(id, Damage::class) ?: add(id, Damage())
      damage.add(10, a)
    }

    world.tick(0.1f) // b's damage (from a) is fully reflected onto a, marked isReflected
    assertEquals(100, world.get(b, Health::class)!!.current)
    assertEquals(100, world.get(a, Health::class)!!.current)
    assertEquals(1, world.get(b, Buffs::class)!!.activeBuffs.size) // not consumed

    world.tick(0.1f) // reflected damage lands on a - and is not bounced back onto b again
    assertEquals(90, world.get(a, Health::class)!!.current)
    assertEquals(100, world.get(b, Health::class)!!.current)
  }
}
