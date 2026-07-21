package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [StatusEffectDurationSystem] and [StatusValueRecalcSystem] wired together against a
 * real [World], the same way [net.bestia.zone.ecs.EcsConfiguration] wires them in production
 * (minus Spring) - to verify the cross-system, recalc-from-scratch behavior, not just each
 * component in isolation.
 */
class StatusEffectSystemsIntegrationTest {

  /** A stand-in [StatusEffectScript] for a flat speed multiplier, registered by simple class name. */
  private class SpeedBuffScript(
    override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION,
    private val speedMultiplier: Float = 1.5f,
    private val duration: Double = 1.0
  ) : StatusEffectScript {
    override fun durationSeconds(level: Int): Double = duration
    override fun apply(context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
      context.speed *= speedMultiplier
    }
  }

  private val speedEffect = StatusEffectDefinition(
    id = 1L,
    identifier = "TEST_SPEED_BUFF",
    isSyncedToClient = true,
    script = "SpeedBuffScript"
  )

  private fun newWorld(script: StatusEffectScript): Pair<World, StatusEffectDefinitionRegistry> {
    val definitionRegistry = StatusEffectDefinitionRegistry()
    definitionRegistry.load(listOf(speedEffect))

    val scriptRegistry = StatusEffectScriptRegistry(listOf(script))

    val world = testWorld(
      systems = listOf(
        StatusEffectDurationSystem(),
        StatusValueRecalcSystem(definitionRegistry, scriptRegistry)
      )
    )
    return world to definitionRegistry
  }

  private fun World.seedStatusValues(entity: EntityId) {
    add(entity, BaseStatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
    add(entity, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
  }

  @Test
  fun `a speed effect raises effective speed and reverts once it expires`() {
    val (world, registry) = newWorld(SpeedBuffScript(speedMultiplier = 1.5f, duration = 1.0))
    val entity = world.create()
    world.add(entity, Speed(2.0f))
    world.seedStatusValues(entity)

    StatusEffectService(registry, StatusEffectScriptRegistry(listOf(SpeedBuffScript(duration = 1.0))))
      .applyEffect(world, entity, definitionId = speedEffect.id, level = 1)

    world.tick(0.1f)
    assertEquals(3.0f, world.get(entity, Speed::class)!!.speed, 0.001f)

    world.tick(1.0f) // fully expires the 1s duration
    assertTrue(world.get(entity, StatusEffects::class)!!.activeEffects.isEmpty())

    world.tick(0.1f) // recalc picks up the dirty marker deferred by the expiry tick
    assertEquals(2.0f, world.get(entity, Speed::class)!!.speed, 0.001f)
  }

  @Test
  fun `re-applying a REFRESH_DURATION effect resets its remaining time instead of stacking`() {
    val (world, registry) = newWorld(SpeedBuffScript(duration = 1.0))
    val entity = world.create()
    world.add(entity, Speed(2.0f))
    world.seedStatusValues(entity)
    val service = StatusEffectService(registry, StatusEffectScriptRegistry(listOf(SpeedBuffScript(duration = 1.0))))

    service.applyEffect(world, entity, definitionId = speedEffect.id, level = 1)
    world.tick(0.5f)
    service.applyEffect(world, entity, definitionId = speedEffect.id, level = 1)

    assertEquals(1, world.get(entity, StatusEffects::class)!!.activeEffects.size)
    assertEquals(1.0f, world.get(entity, StatusEffects::class)!!.activeEffects.single().remainingSeconds, 0.001f)
  }
}
