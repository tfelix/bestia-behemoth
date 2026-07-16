package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.buff.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.buff.StatusEffect
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Recomputes each entity with status effects' [StatModifiers] from its active [StatusEffects] every tick. Runs after
 * [StatusEffectDamageInterceptSystem] (45) and [StatusEffectDurationSystem] (46) so an effect that triggered or
 * expired this tick is already reflected before stats are recomputed.
 */
@SpringComponent
@Order(47)
class StatAggregationSystem(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry
) : System {

  override val reads: ComponentClassSet = setOf(StatusEffects::class)
  override val writes: ComponentClassSet = setOf(StatModifiers::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(StatusEffects::class).each { id ->
      val effects = get<StatusEffects>()
      val modifiers = world.get(id, StatModifiers::class) ?: world.add(id, StatModifiers())
      modifiers.clear()

      for (active in effects.activeEffects) {
        val definition = statusEffectDefinitionRegistry.findById(active.definitionId) ?: continue
        for (effect in definition.effects) {
          if (effect is StatusEffect.StatModifierEffect) {
            modifiers.addModifier(effect.stat, effect.mode, (effect.valuePerLevel * active.level).toFloat())
          }
        }
      }
    }
  }

  companion object {
    /**
     * Ensures [entityId] already has a [StatModifiers] component before the next tick runs.
     * Called from [net.bestia.zone.battle.StatusEffectService.applyEffect], which always runs outside a
     * System (from a message handler, never mid-tick), so [World.add] here is synchronous - unlike
     * the `world.add` a couple lines up in [update], which runs mid-tick and would otherwise only
     * become visible to [SpeedModifierSystem] one tick late (the same deferred-structural-change
     * behavior documented on [StatusEffectDamageInterceptSystem]).
     */
    fun ensureStatModifiers(world: World, entityId: EntityId) {
      world.get(entityId, StatModifiers::class) ?: world.add(entityId, StatModifiers())
    }
  }
}
