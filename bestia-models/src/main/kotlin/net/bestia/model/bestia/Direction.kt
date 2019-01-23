package net.bestia.model.bestia

/**
 * Direction of the facing of certain elements. Some sprites will have a facing
 * direction which needs to be preserved.
 *
 * @author Thomas Felix
 */
enum class Direction {
  NORTH,
  NORTH_EAST,
  EAST,
  SOUTH_EAST,
  SOUTH,
  SOUTH_WEST,
  WEST,
  NORTH_WEST
}

fun randomDirection(): Direction {
  val i = Math.round(Math.random() * Direction.values().size).toInt()

  return Direction.values()[i]
}
