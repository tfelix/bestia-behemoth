package net.bestia.zone.battle.ecs.attack

import net.bestia.zone.ecs.core.Component

/**
 * Until when a Chain Combo may follow the Triple Blow this entity landed last, see `TripleBlow`. Spent by setting it
 * to zero rather than by a removal, which would only apply at the end of the tick.
 */
class ComboOpening(
  var closesAtMillis: Long = 0,
) : Component {

  fun isOpenAt(nowMillis: Long): Boolean {
    return nowMillis < closesAtMillis
  }
}
