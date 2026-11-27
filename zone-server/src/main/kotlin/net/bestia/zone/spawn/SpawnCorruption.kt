package net.bestia.zone.spawn

/** How corrupted the land is at a place, 0 to 1, for the wild spawns. A port, because the doomsday sits above it. */
fun interface SpawnCorruption {

  /** At [x], [y] in metres. Read on the tick. */
  fun corruptionAt(x: Double, y: Double): Double

  companion object {
    /** From here on, the land breeds corrupted bestia. */
    const val BREEDS_CORRUPTED = 0.5
  }
}

/** Whether a wild bestia born at the voxel column [x], [y] is corrupted. */
fun isCorrupted(corruption: SpawnCorruption?, x: Long, y: Long): Boolean {
  return corruption != null && corruption.corruptionAt(x.toDouble(), y.toDouble()) >= SpawnCorruption.BREEDS_CORRUPTED
}
