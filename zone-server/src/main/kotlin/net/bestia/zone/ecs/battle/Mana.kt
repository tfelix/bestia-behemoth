package net.bestia.zone.ecs.battle

import net.bestia.zone.ecs2.Component
import net.bestia.zone.status.CurMax

class Mana(
  current: Int,
  max: Int
) : Component {

  private val data = CurMax().apply {
    this.max = max
    this.current = current
  }

  var current
    get() = data.current
    set(value) {
      data.current = value
    }

  var max
    get() = data.max
    set(value) {
      data.max = value
    }
}