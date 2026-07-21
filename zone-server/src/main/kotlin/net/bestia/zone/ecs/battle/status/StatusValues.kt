package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component

/**
 * An entity's current, effective status values - [BaseStatusValues] with every active status
 * effect (and later, equipment) applied. Written only by
 * `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem`; everything else (regen systems,
 * [net.bestia.zone.battle.BattleContextFactory]) only ever reads it.
 */
data class StatusValues(
  var strength: Int,
  var intelligence: Int,
  var vitality: Int,
  var dexterity: Int,
  var willpower: Int,
  var agility: Int
) : Component
