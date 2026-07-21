package net.bestia.zone.battle.status

import net.bestia.zone.ecs.battle.status.BaseStatusValues

/**
 * Mutable working set a [StatusEffectScript] writes into while
 * [net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem] rebuilds an entity's effective
 * status values from scratch. Seeded from [BaseStatusValues] plus whatever other base values feed
 * into the recalc (currently just [baseSpeed]); starts equal to the unbuffed values, then every
 * active effect's script mutates it in turn.
 */
class StatusValueRecalcContext(
  base: BaseStatusValues,
  baseSpeed: Float
) {
  var strength: Int = base.strength
  var intelligence: Int = base.intelligence
  var vitality: Int = base.vitality
  var dexterity: Int = base.dexterity
  var willpower: Int = base.willpower
  var agility: Int = base.agility
  var speed: Float = baseSpeed
}
