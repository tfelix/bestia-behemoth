package net.bestia.zone.battle.status

/**
 * A single effect a [StatusEffectDefinition] applies while active. Extensible without touching any
 * dispatch system: [StatModifierEffect] is consumed by
 * [net.bestia.zone.ecs.battle.effects.StatAggregationSystem], [TriggerEffect] by
 * [net.bestia.zone.ecs.battle.effects.StatusEffectDamageInterceptSystem]; a future effect kind is a new
 * sealed subtype plus one new consumer system.
 */
sealed interface StatusEffect {
  data class StatModifierEffect(
    val stat: StatType,
    val mode: ModifierMode,
    val valuePerLevel: Double
  ) : StatusEffect

  data class TriggerEffect(
    val on: StatusEffectTriggerEvent,
    val action: StatusEffectTriggerAction,
    val consumeOnTrigger: Boolean = true
  ) : StatusEffect
}
