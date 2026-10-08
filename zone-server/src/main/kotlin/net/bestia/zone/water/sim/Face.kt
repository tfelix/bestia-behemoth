package net.bestia.zone.water.sim

/** The six neighbours of a cell, in the order a [WaterChunk] keeps its links to neighbouring chunks. */
enum class Face(val dx: Int, val dy: Int, val dz: Int) {
  DOWN(0, 0, -1),
  UP(0, 0, 1),
  WEST(-1, 0, 0),
  EAST(1, 0, 0),
  SOUTH(0, -1, 0),
  NORTH(0, 1, 0);

  companion object {
    /** The four a cell spreads to. The stepper rotates where in this list it starts, so no side is favoured. */
    val LATERAL = listOf(WEST, EAST, SOUTH, NORTH)
  }
}
