package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component

/**
 * The unbuffed, seed status values for an entity - set once at creation and never touched by
 * status effects. [StatusValues] is the effective, current counterpart recomputed from this by
 * `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem`, the same base/effective split
 * [net.bestia.zone.ecs.movement.Speed] already uses for `baseSpeed`/`speed`.
 *
 * No allocation/leveling UI exists yet, so entities are seeded with placeholder starting values.
 */
data class BaseStatusValues(
  val strength: Int,
  val intelligence: Int,
  val vitality: Int,
  val dexterity: Int,
  val willpower: Int,
  val agility: Int
) : Component
