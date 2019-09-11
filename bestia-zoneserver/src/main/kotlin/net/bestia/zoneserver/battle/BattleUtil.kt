package net.bestia.zoneserver.battle

import kotlin.math.max
import kotlin.math.min

fun Float.clamp(min: Float, max: Float = Float.MAX_VALUE): Float {
  return max(min, min(max, this))
}
