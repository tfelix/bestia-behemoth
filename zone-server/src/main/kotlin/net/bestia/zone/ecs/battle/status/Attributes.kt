package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component

/**
 * Base attributes feeding derived stats (e.g. carry capacity). No allocation/leveling UI exists
 * yet, so entities are seeded with placeholder starting values.
 */
data class Attributes(
  var strength: Int,
  var intelligence: Int,
  var vitality: Int,
  var dexterity: Int,
  var willpower: Int,
  var agility: Int
) : Component
