package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.Component

/** Stamina a worker owes for its labour below one whole point, carried over so that small units add up. */
class WorkStrain : Component {
  var owedStamina: Double = 0.0
}
