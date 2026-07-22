package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.util.EntityId
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
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry,
  private val conditionValueCalculator: ConditionValueCalculator
) : System {

  override val reads: ComponentClassSet = setOf(
    BaseStatusValues::class,
    StatusEffects::class,
    IsStatusValueDirty::class,
    Level::class
  )
  override val writes: ComponentClassSet = setOf(
    StatusValues::class,
    Speed::class,
    IsStatusValueDirty::class,
    Health::class,
    Mana::class,
    Stamina::class
  )

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

      recomputeConditionMaxima(world, id, context)

      world.remove(id, IsStatusValueDirty::class)
    }
  }

  /**
   * Recomputes the max HP/Mana/Stamina pools from the freshly rebuilt effective attributes for
   * entities that opt into formula-driven vitals ([FormulaDrivenVitals]). Mobs lack the marker and
   * keep their authored pool. `CurMax.max` re-clamps `current`, so a shrunken pool never leaves a
   * character above its new maximum.
   */
  private fun recomputeConditionMaxima(world: World, id: EntityId, context: StatusValueRecalcContext) {
    val level = world.get(id, Level::class)?.level ?: 1

    world.get(id, Health::class)?.let { it.max = conditionValueCalculator.computeMaxHp(level, context.vitality) }
    world.get(id, Mana::class)?.let { it.max = conditionValueCalculator.computeMaxMana(level, context.intelligence) }
    world.get(id, Stamina::class)?.let {
      it.max = conditionValueCalculator.computeMaxStamina(level, context.vitality, context.strength, context.willpower)
    }
  }
}
