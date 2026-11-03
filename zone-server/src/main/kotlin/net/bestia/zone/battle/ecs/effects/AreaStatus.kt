package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.status.StatusEffectId

/** A status an area rolls on each foe inside, every tick. Null [durationSeconds] keeps the script's own. */
data class AreaStatus(
  val effect: StatusEffectId,
  val chancePercent: Int,
  val durationSeconds: Double?,
  val level: Int,
)
