package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs.Component
import net.bestia.zone.status.CurMax

class Mana(
  current: Int,
  max: Int
) : CurMax(), Component {

  init {
    this.max = max
    this.current = current
  }
}