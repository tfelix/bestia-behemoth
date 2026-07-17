package net.bestia.zone.battle.status

/**
 * A stat that a [net.bestia.zone.battle.effect.BuffEffect.StatModifierEffect] can modify.
 *
 * Intentionally starts with only [SPEED]: [net.bestia.zone.ecs.movement.Speed] is the only
 * live, mutable, synced stat component today. Add a member only alongside the consumer system
 * that actually reads [net.bestia.zone.ecs.battle.effects.StatModifiers.effective] for it - a stat
 * type nothing reads would let a buff silently do nothing.
 */
enum class StatType {
  SPEED
}
