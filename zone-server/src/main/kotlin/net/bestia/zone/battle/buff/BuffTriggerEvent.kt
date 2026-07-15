package net.bestia.zone.battle.buff

/**
 * Gameplay events a [net.bestia.zone.ecs.battle.buff.StatusEffectDamageInterceptSystem]-style system
 * checks a target's active effects against. Add a member only alongside the system that actually
 * fires it.
 */
enum class StatusEffectTriggerEvent {
  ON_DAMAGE_TAKEN
}
