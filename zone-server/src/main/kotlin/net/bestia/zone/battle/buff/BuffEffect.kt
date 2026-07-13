package net.bestia.zone.battle.buff

import net.bestia.zone.battle.status.StatType

/**
 * A single effect a [BuffDefinition] applies while active. Extensible without touching any
 * dispatch system: [StatModifierEffect] is consumed by
 * [net.bestia.zone.ecs.battle.buff.StatAggregationSystem], [TriggerEffect] by
 * [net.bestia.zone.ecs.battle.buff.BuffDamageInterceptSystem]; a future effect kind is a new
 * sealed subtype plus one new consumer system.
 */
sealed interface BuffEffect {
  data class StatModifierEffect(
    val stat: StatType,
    val mode: ModifierMode,
    val valuePerLevel: Double
  ) : BuffEffect

  data class TriggerEffect(
    val on: BuffTriggerEvent,
    val action: BuffTriggerAction,
    val consumeOnTrigger: Boolean = true
  ) : BuffEffect
}
