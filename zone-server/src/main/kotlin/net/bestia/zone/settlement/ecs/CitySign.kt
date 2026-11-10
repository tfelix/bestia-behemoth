package net.bestia.zone.settlement.ecs

import net.bestia.zone.ecs.core.Component

/** The standing sign of player town [townId]. Its health is the town's to lose. */
class CitySign(val townId: Long) : Component {

  /** Seconds since the sign last lost health; it heals once this passes the docs' 5 minutes. */
  var unhurtSeconds: Float = 0f

  /** Health at the last tick, to see whether it was hit since. */
  var lastHp: Int = -1

  /** Healing that has not added up to a whole hit point yet. */
  var pendingHeal: Float = 0f
}
