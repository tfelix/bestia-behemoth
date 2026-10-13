package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.passive.PassiveSkillScriptRegistry
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueContributor
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.OwnerCombatStats
import net.bestia.zone.battle.ecs.status.RegenerationModifiers
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.FormulaDrivenVitals
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.CombatValuesPool
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.util.EntityId
import kotlin.collections.orEmpty
import org.springframework.stereotype.Component as SpringComponent
import net.bestia.zone.ecs.core.update

/**
 * Rebuilds an entity's [CombatStats] (and [Speed.speed], and [RegenerationModifiers]) from scratch for every
 * entity marked [IsStatusValueDirty]: starts from its permanent attributes - an owner's [BaseStatusValues], or the
 * species' own that a mob keeps in its [CombatStats] - and [Speed.baseSpeed], folds
 * in every learned [net.bestia.zone.battle.passive.PassiveSkillScript], then every worn item's
 * [net.bestia.zone.item.equip.script.EquipmentScript.apply], then runs every active [StatusEffects]
 * instance's [net.bestia.zone.battle.status.StatusEffectScript.apply] over the result in turn, then
 * writes the final values back and clears the dirty marker.
 *
 * The result is a [CombatValues] assigned into the entity's [CombatStats] in place, so later systems see it this tick.
 * An owner's is its own and also goes to its [OwnerCombatStats]; anything else's goes through [CombatValuesPool], so
 * mobs under equal buffs - or none - share one.
 *
 * Passives and equipment are applied before effects because effect scripts are the multiplicative
 * ones (`context.speed *= …`), and a percentage buff should scale the innate, geared value rather
 * than the naked one. Passives before equipment is convention rather than arithmetic - both are
 * additive today - but it keeps "innate, then worn, then temporary" reading in the order a player
 * would describe it. Note the regeneration modifiers are order-independent by construction: they
 * accumulate as a flat and a percentage sum resolved once, at regen time.
 *
 * Runs after [StatusEffectDurationSystem] so an effect that expired this tick is already
 * gone before values are rebuilt.
 */
@SpringComponent
class StatusValueRecalcSystem(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry,
  private val equipment: StatusValueContributor,
  private val passiveSkillScriptRegistry: PassiveSkillScriptRegistry,
  private val conditionValueCalculator: ConditionValueCalculator,
  private val combatValuesPool: CombatValuesPool
) : System {
  override val phase = Phase.STATUS
  override val after = setOf(StatusEffectDurationSystem::class)

  override val reads: ComponentClassSet = setOf(
    BaseStatusValues::class,
    StatusEffects::class,
    IsStatusValueDirty::class,
    Level::class,
    FormulaDrivenVitals::class,
    KnownSkills::class,
    // A status effect script may address the entity's owner (MasterIntroMarker greets them).
    Account::class,
    Master::class
  ) + equipment.reads
  override val writes: ComponentClassSet = setOf(
    StatusValues::class,
    Speed::class,
    IsStatusValueDirty::class,
    Health::class,
    Mana::class,
    Stamina::class,
    RegenerationModifiers::class,
    CombatStats::class,
    OwnerCombatStats::class
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(IsStatusValueDirty::class).each { id ->
      // A mob or prop keeps its permanent attributes in the block it was spawned with.
      val base = world.get(id, BaseStatusValues::class)?.toAttributes()
        ?: world.get(id, CombatStats::class)?.values?.base
        ?: run {
          world.remove(id, IsStatusValueDirty::class)
          return@each
        }
      val baseSpeed = world.get(id, Speed::class)?.baseSpeed ?: 0f
      val level = world.get(id, Level::class)?.level ?: 1

      val context = StatusValueRecalcContext(base, baseSpeed)

      applyPassiveSkills(context, world, id)
      equipment.contribute(context, world, id)
      applyStatusEffects(context, world, id)

      // Only an owner carries this outbound copy; nothing on the server reads it.
      world.get(id, StatusValues::class)?.let { values ->
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

      publishCombatValues(world, id, CombatValues.fromContext(level, base, context))

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
   * Pooled for anything unowned, so a mob that took a buff and lost it holds its species' block again rather than an
   * equal copy. [CombatStats] is assigned in place: replacing it would defer to the end of the tick.
   */
  private fun publishCombatValues(world: World, id: EntityId, resolved: CombatValues) {
    val owner = world.get(id, OwnerCombatStats::class)
    owner?.update(resolved)

    val values = if (owner == null) combatValuesPool.share(resolved) else resolved

    world.update(id, default = { CombatStats(values) }) { it.values = values }
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
