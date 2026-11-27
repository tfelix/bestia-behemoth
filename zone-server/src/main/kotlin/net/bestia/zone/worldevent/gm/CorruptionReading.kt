package net.bestia.zone.worldevent.gm

/** How corrupted the land is at a place, 0 to 1, for the GM layer. A port, because the doomsday sits above it. */
fun interface CorruptionReading {
  fun corruptionAt(x: Double, y: Double): Double
}
