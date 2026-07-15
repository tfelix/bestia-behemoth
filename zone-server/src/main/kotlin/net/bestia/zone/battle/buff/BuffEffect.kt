package net.bestia.zone.battle.buff

import net.bestia.zone.battle.status.StatType

/**
 * A single effect a [StatusEffectDefinition] applies while active. Extensible without touching any
 * dispatch system: [StatModifierEffect] is consumed by
 * [net.bestia.zone.ecs.battle.buff.StatAggregationSystem], [TriggerEffect] by
 * [net.bestia.zone.ecs.battle.buff.StatusEffectDamageInterceptSystem]; a future effect kind is a new
 * sealed subtype plus one new consumer system.
 */
sealed interface StatusEffectEffect {
  data class StatModifierEffect(
    val stat: StatType,
    val mode: ModifierMode,
    val valuePerLevel: Double
  ) : StatusEffectEffect

  data class TriggerEffect(
    val on: StatusEffectTriggerEvent,
    val action: StatusEffectTriggerAction,
    val consumeOnTrigger: Boolean = true
  ) : StatusEffectEffect
}
