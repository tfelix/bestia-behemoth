package net.bestia.zoneserver.battle

fun Float.between(min: Float, max: Float): Float {
  return Math.max(min, Math.min(max, this))
}
