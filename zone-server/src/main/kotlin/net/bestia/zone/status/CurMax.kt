package net.bestia.zone.status

import kotlin.math.max
import kotlin.math.min

class CurMax {
  var current: Int = 0
    set(value) {
      field = max(0, min(value, max))
    }

  var max: Int = 0
    set(value) {
      require(value >= 0)
      field = value

      if (current > value) {
        current = value
      }
    }

  fun copy(): CurMax {
    return CurMax().apply {
      this.max = max
      this.current = current
    }
  }

  override fun toString(): String {
    return "$current/$max"
  }
}