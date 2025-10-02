package net.bestia.zone.ecs.battle

import net.bestia.zone.status.CurMax
import net.bestia.zone.ecs.Component

class Health(
  current: Int,
  max: Int
) : CurMax(), Component {

  init {
    this.max = max
    this.current = current
  }
}