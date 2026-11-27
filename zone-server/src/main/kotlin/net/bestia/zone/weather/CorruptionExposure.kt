package net.bestia.zone.weather

/** How corrupted the land is at a place, 0 to 1. A port, because the doomsday sits above the weather. */
fun interface CorruptionExposure {

  /** At [x], [y] in metres. Read on the tick. */
  fun corruptionAt(x: Double, y: Double): Double
}
