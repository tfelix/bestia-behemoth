package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Speed
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Rebuilds [StatusValues] (and [Speed.speed]) from scratch for every entity marked
 * [IsStatusValueDirty]: starts from [BaseStatusValues] (and [Speed.baseSpeed]), runs every active
 * [StatusEffects] instance's [net.bestia.zone.battle.status.StatusEffectScript.apply] over the
 * result in turn, then writes the final values back and clears the dirty marker.
 *
 * Mirrors rAthena's `status_calc_bl`: the whole value is recomputed from base + all active
 * modifiers every time, rather than incrementally aggregating individual modifier deltas (the
 * old `StatAggregationSystem`/`StatModifiers`/`SpeedModifierSystem` approach this replaces).
 * Runs after [StatusEffectDurationSystem] (46) so an effect that expired this tick is already
 * gone before values are rebuilt.
 */
@SpringComponent
@Order(47)
class StatusValueRecalcSystem(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry
) : System {

  override val reads: ComponentClassSet = setOf(
    BaseStatusValues::class,
    StatusEffects::class,
    IsStatusValueDirty::class
  )
  override val writes: ComponentClassSet = setOf(StatusValues::class, Speed::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(IsStatusValueDirty::class).each { id ->
      val base = world.get(id, BaseStatusValues::class) ?: run {
        world.remove(id, IsStatusValueDirty::class)
        return@each
      }
      val baseSpeed = world.get(id, Speed::class)?.baseSpeed ?: 0f

      val context = StatusValueRecalcContext(base, baseSpeed)
      val activeEffects = world.get(id, StatusEffects::class)?.activeEffects.orEmpty()

      for (active in activeEffects) {
        val definition = statusEffectDefinitionRegistry.findById(active.definitionId) ?: continue
        val script = statusEffectScriptRegistry.get(definition.script) ?: continue
        script.apply(context, active.level, active.sourceEntityId)
      }

      world.update(id, default = { StatusValues(context.strength, context.intelligence, context.vitality, context.dexterity, context.willpower, context.agility) }) { values ->
        values.strength = context.strength
        values.intelligence = context.intelligence
        values.vitality = context.vitality
        values.dexterity = context.dexterity
        values.willpower = context.willpower
        values.agility = context.agility
      }

      world.get(id, Speed::class)?.let { speed -> speed.speed = context.speed }

      world.remove(id, IsStatusValueDirty::class)
    }
  }
}
