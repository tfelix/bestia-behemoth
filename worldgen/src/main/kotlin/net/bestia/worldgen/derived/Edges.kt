package net.bestia.worldgen.derived

/**
 * The four sides of a column as bits, for walls that stand on a border between two columns.
 *
 * Listed counter-clockwise, so a quarter turn is a rotation of the four bits and the opposite side is two turns.
 */
object Edges {

  const val NONE = 0
  const val X_PLUS = 1
  const val Y_PLUS = 2
  const val X_MINUS = 4
  const val Y_MINUS = 8
  const val ALL = X_PLUS or Y_PLUS or X_MINUS or Y_MINUS

  /** The sides a step of ([dx], [dy]) leaves its column through: two of them for a diagonal. */
  fun crossedBy(dx: Int, dy: Int): Int {
    var edges = NONE
    if (dx > 0) edges = edges or X_PLUS
    if (dx < 0) edges = edges or X_MINUS
    if (dy > 0) edges = edges or Y_PLUS
    if (dy < 0) edges = edges or Y_MINUS

    return edges
  }

  /** [edges] turned counter-clockwise by [quarterTurns]: x+ becomes y+. */
  fun rotated(edges: Int, quarterTurns: Int): Int {
    val turns = Math.floorMod(quarterTurns, 4)

    return ((edges shl turns) or (edges shr (4 - turns))) and ALL
  }

  /** The same sides seen from the column on the other side of them. */
  fun opposite(edges: Int): Int {
    return rotated(edges, 2)
  }
}
