package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.skill.passive.PassiveSkillScriptRegistry
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.RegenerationModifiers
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.FormulaDrivenVitals
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.Mana
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.util.EntityId
import org.springframework.core.annotation.Order
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.orEmpty
import org.springframework.stereotype.Component as SpringComponent

/**
 * Rebuilds [StatusValues] (and [Speed.speed], and [RegenerationModifiers]) from scratch for every
 * entity marked [IsStatusValueDirty]: starts from [BaseStatusValues] (and [Speed.baseSpeed]), folds
 * in every learned [net.bestia.zone.battle.skill.passive.PassiveSkillScript], then every worn item's
 * [net.bestia.zone.item.equip.script.EquipmentScript.apply], then runs every active [StatusEffects]
 * instance's [net.bestia.zone.battle.status.StatusEffectScript.apply] over the result in turn, then
 * writes the final values back and clears the dirty marker.
 *
 * Passives and equipment are applied before effects because effect scripts are the multiplicative
 * ones (`context.speed *= …`), and a percentage buff should scale the innate, geared value rather
 * than the naked one. Passives before equipment is convention rather than arithmetic - both are
 * additive today - but it keeps "innate, then worn, then temporary" reading in the order a player
 * would describe it. Note the regeneration modifiers are order-independent by construction: they
 * accumulate as a flat and a percentage sum resolved once, at regen time.
 *
 * Runs after [StatusEffectDurationSystem] (46) so an effect that expired this tick is already
 * gone before values are rebuilt.
 */
@SpringComponent
@Order(47)
class StatusValueRecalcSystem(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry,
  private val equipmentScriptRegistry: EquipmentScriptRegistry,
  private val passiveSkillScriptRegistry: PassiveSkillScriptRegistry,
  private val conditionValueCalculator: ConditionValueCalculator
) : System {

  override val reads: ComponentClassSet = setOf(
    BaseStatusValues::class,
    StatusEffects::class,
    Equipment::class,
    IsStatusValueDirty::class,
    Level::class,
    FormulaDrivenVitals::class,
    KnownSkills::class
  )
  override val writes: ComponentClassSet = setOf(
    StatusValues::class,
    Speed::class,
    IsStatusValueDirty::class,
    Health::class,
    Mana::class,
    Stamina::class,
    RegenerationModifiers::class
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(IsStatusValueDirty::class).each { id ->
      val base = world.get(id, BaseStatusValues::class) ?: run {
        world.remove(id, IsStatusValueDirty::class)
        return@each
      }
      val baseSpeed = world.get(id, Speed::class)?.baseSpeed ?: 0f

      val context = StatusValueRecalcContext(base, baseSpeed)

      applyPassiveSkills(context, world, id)
      applyEquipmentEffects(context, world, id)
      applyStatusEffects(context, world, id)

      world.update(
        id,
        default = {
          StatusValues(
            context.strength,
            context.intelligence,
            context.vitality,
            context.dexterity,
            context.willpower,
            context.agility
          )
        }) { values ->
        values.strength = context.strength
        values.intelligence = context.intelligence
        values.vitality = context.vitality
        values.dexterity = context.dexterity
        values.willpower = context.willpower
        values.agility = context.agility
        values.markDirty()
      }

      world.get(id, Speed::class)?.let { speed -> speed.speed = context.speed }

      // Overwrite, never accumulate: the context was rebuilt from scratch above, so a buff that has
      // since expired contributes nothing to it and must stop contributing here too. On the first
      // pass the component may not exist yet - `update` mutates the instance it just created even
      // when the structural add is deferred to the end of the tick, so no values are lost, only the
      // tick on which the component becomes queryable. Invisible against a 6-10s regen cadence,
      // which is why the spawners deliberately do not pre-seed it.
      world.update(id, default = { RegenerationModifiers() }) { it.copyFrom(context) }

      recomputeConditionMaxima(world, id, context)

      world.remove(id, IsStatusValueDirty::class)
    }
  }

  /**
   * Folds in every passive skill this entity has actually invested in. Iterates the (few) scripted
   * passives and asks [KnownSkills] for each level rather than the other way round - see
   * [PassiveSkillScriptRegistry.bound].
   */
  private fun applyPassiveSkills(
    context: StatusValueRecalcContext,
    world: World,
    id: EntityId
  ) {
    val knownSkills = world.get(id, KnownSkills::class) ?: return

    for ((skillId, script) in passiveSkillScriptRegistry.bound()) {
      val level = knownSkills.levelOf(skillId)
      if (level > 0) {
        script.apply(context, level)
      }
    }
  }

  private fun applyEquipmentEffects(
    context: StatusValueRecalcContext,
    world: World,
    id: EntityId
  ) {
    val worn = world.get(id, Equipment::class)?.getWorn().orEmpty()
    for ((slot, item) in worn) {
      val script = equipmentScriptRegistry.getByItemId(item.itemId) ?: continue
      script.apply(context, slot, item.upgradeLevel)
    }
  }

  private fun applyStatusEffects(
    context: StatusValueRecalcContext,
    world: World,
    id: EntityId
  ) {
    // Copied rather than iterated live: a script is allowed to remove itself from within `apply`
    // (MasterIntroMarker does), which would otherwise mutate the list mid-iteration.
    val activeEffects = world.get(id, StatusEffects::class)?.activeEffects?.toList().orEmpty()

    for (active in activeEffects) {
      val definition = statusEffectDefinitionRegistry.findById(active.definitionId) ?: continue
      val script = statusEffectScriptRegistry.get(definition.script) ?: continue
      script.apply(world, id, context, active.level, active.sourceEntityId)
    }
  }

  /**
   * Recomputes the max HP/Mana/Stamina pools from the freshly rebuilt effective attributes for
   * entities that opt into formula-driven vitals ([FormulaDrivenVitals]). Mobs lack the marker and
   * keep their authored pool. `CurMax.max` re-clamps `current`, so a shrunken pool never leaves a
   * character above its new maximum.
   *
   * The gate sits here rather than on the query in [update] on purpose: a mob still needs its
   * [StatusValues] and [Speed] rebuilt so buffs and slows land on it, it is only the pool maxima
   * that are content rather than formula.
   */
  private fun recomputeConditionMaxima(world: World, id: EntityId, context: StatusValueRecalcContext) {
    if (!world.has(id, FormulaDrivenVitals::class)) return

    val level = world.get(id, Level::class)?.level ?: 1

    world.get(id, Health::class)?.let { it.max = conditionValueCalculator.computeMaxHp(level, context.vitality) }
    world.get(id, Mana::class)?.let { it.max = conditionValueCalculator.computeMaxMana(level, context.intelligence) }
    world.get(id, Stamina::class)?.let {
      it.max = conditionValueCalculator.computeMaxStamina(level, context.vitality, context.strength, context.willpower)
    }
  }
}
