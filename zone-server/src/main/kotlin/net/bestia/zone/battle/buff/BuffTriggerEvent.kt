package net.bestia.zone.battle.buff

/**
 * Gameplay events a [net.bestia.zone.ecs.battle.buff.BuffDamageInterceptSystem]-style system
 * checks a target's active buffs against. Add a member only alongside the system that actually
 * fires it.
 */
enum class BuffTriggerEvent {
  ON_DAMAGE_TAKEN
}
