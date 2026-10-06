package net.bestia.zone.battle.ecs.exp

import net.bestia.zone.ecs.core.Component

/**
 * Helper component which is used to track entities with new EXP which helps to very specifically re-calculate
 * status updates when an actual level up has happened.
 */
data class GainExp(
  var value: Int = 0,
) : Component
