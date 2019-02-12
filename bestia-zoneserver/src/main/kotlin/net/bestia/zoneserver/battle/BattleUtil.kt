package net.bestia.zoneserver.battle

fun Float.clamp(min: Float, max: Float = Float.MAX_VALUE): Float {
  return Math.max(min, Math.min(max, this))
}
