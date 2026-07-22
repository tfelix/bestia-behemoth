package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component

/**
 * The unbuffed status values for an entity - untouched by status effects, but permanently raised
 * by investing a [StatusPoints] point. [StatusValues] is the effective, current counterpart
 * recomputed from this by `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem`, the same
 * base/effective split [net.bestia.zone.ecs.movement.Speed] already uses for `baseSpeed`/`speed`.
 */
data class BaseStatusValues(
  var strength: Int,
  var intelligence: Int,
  var vitality: Int,
  var dexterity: Int,
  var willpower: Int,
  var agility: Int
) : Component
