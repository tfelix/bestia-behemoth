package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.battle.buff.BuffDefinitionRegistry
import net.bestia.zone.battle.buff.BuffEffect
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Recomputes each buffed entity's [StatModifiers] from its active [Buffs] every tick. Runs after
 * [BuffDamageInterceptSystem] (45) and [BuffDurationSystem] (46) so a buff that triggered or
 * expired this tick is already reflected before stats are recomputed.
 */
@SpringComponent
@Order(47)
class StatAggregationSystem(
  private val buffDefinitionRegistry: BuffDefinitionRegistry
) : System {

  override val reads: ComponentClassSet = setOf(Buffs::class)
  override val writes: ComponentClassSet = setOf(StatModifiers::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Buffs::class).each { id ->
      val buffs = get<Buffs>()
      val modifiers = world.get(id, StatModifiers::class) ?: world.add(id, StatModifiers())
      modifiers.clear()

      for (active in buffs.activeBuffs) {
        val definition = buffDefinitionRegistry.findById(active.definitionId) ?: continue
        for (effect in definition.effects) {
          if (effect is BuffEffect.StatModifierEffect) {
            modifiers.addModifier(effect.stat, effect.mode, effect.valuePerLevel * active.level)
          }
        }
      }
    }
  }

  companion object {
    /**
     * Ensures [entityId] already has a [StatModifiers] component before the next tick runs.
     * Called from [net.bestia.zone.battle.BuffService.applyBuff], which always runs outside a
     * System (from a message handler, never mid-tick), so [World.add] here is synchronous - unlike
     * the `world.add` a couple lines up in [update], which runs mid-tick and would otherwise only
     * become visible to [SpeedModifierSystem] one tick late (the same deferred-structural-change
     * behavior documented on [BuffDamageInterceptSystem]).
     */
    fun ensureStatModifiers(world: World, entityId: EntityId) {
      world.get(entityId, StatModifiers::class) ?: world.add(entityId, StatModifiers())
    }
  }
}
