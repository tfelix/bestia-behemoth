package net.bestia.zoneserver.battle

import kotlin.math.max
import kotlin.math.min

fun Float.clamp(min: Float, max: Float = Float.MAX_VALUE): Float {
  if (this.isNaN()) {
    return min
  }
  return max(min, min(max, this))
}

fun Double.clamp(min: Double, max: Double = Double.MAX_VALUE): Double {
  if (this.isNaN()) {
    return min
  }
  return max(min, min(max, this))
}

