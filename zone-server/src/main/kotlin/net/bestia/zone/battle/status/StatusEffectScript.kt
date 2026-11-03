package net.bestia.zone.battle.status

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * The gameplay logic behind one [StatusEffectDefinition] - what it does to an entity's status
 * values, how long it lasts, and how re-application behaves. Registered under its simple class
 * name (see [StatusEffectScriptRegistry]) and referenced by [StatusEffectDefinition.script],
 * exactly the same pattern as [net.bestia.zone.casting.SkillStrategy] /
 * [net.bestia.zone.casting.SkillStrategyFactory] for skills.
 *
 * Every definition needs one, even a purely bookkeeping effect with nothing to apply - it still
 * needs to answer "how long" and "what happens on re-application".
 */
interface StatusEffectScript {
  val stackBehavior: StackBehavior
    get() = StackBehavior.REFRESH_DURATION

  fun durationSeconds(level: Int): Double

  /** What the bearer may not do while the effect lasts. See [ActionLock]. */
  val actionLocks: Set<ActionLock>
    get() = emptySet()

  /** What harm the effect turns away while it is active. See [HarmShield]. */
  val shield: HarmShield?
    get() = null

  /** False for an effect that is re-derived on spawn, so a stored copy could only ever be stale. */
  val isPersisted: Boolean
    get() = true

  /**
   * False for an effect its cause keeps refreshing, like a ward: the client shows it until it is gone, so a
   * refresh has nothing to tell it.
   */
  val showsCountdown: Boolean
    get() = true

  /**
   * How often [onTick] runs, or null for an effect that does nothing over time. Counted on
   * `StatusEffectDurationSystem`'s one-second beat, so it is effectively whole seconds.
   */
  val tickIntervalSeconds: Float?
    get() = null

  /** Acts over time: an aura handing out a buff, a poison. */
  fun onTick(context: StatusEffectTickContext) {
    // Most effects only change values during recalc.
  }

  /** Runs once when the effect lands, not on a refresh. [apply] runs on every recalc instead. */
  fun onApply(context: StatusEffectHookContext) {
  }

  fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
  }

  /** Answers something the bearer did, like a wound that opens with every swing. */
  fun onActed(context: StatusEffectHookContext, action: EntityAction) {
  }

  /** Returns how much of [amount] the effect lets through to the bearer's health. */
  fun onDamageTaken(context: StatusEffectHookContext, amount: Int): Int {
    return amount
  }

  /** What the recalc calls. An effect that reads its own instance, like an endowment's element, overrides this. */
  fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    apply(world, entityId, context, effect.level, effect.sourceEntityId)
  }

  fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    // Bookkeeping-only effects (e.g. a resisted-once marker) have nothing to apply.
  }
}
