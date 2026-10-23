package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L

/** Waypoints as one short line of text, `x,y,z;x,y,z`, for a column that keeps a route. */
object WaypointText {

  fun encode(waypoints: List<Vec3L>): String {
    return waypoints.joinToString(";") { "${it.x},${it.y},${it.z}" }
  }

  /** Whatever cannot be read is left out rather than refused: a route with one stop fewer still goes. */
  fun decode(text: String): List<Vec3L> {
    if (text.isBlank()) return emptyList()

    return text.split(';').mapNotNull { point ->
      val parts = point.split(',').mapNotNull { it.trim().toLongOrNull() }
      if (parts.size == 3) Vec3L(parts[0], parts[1], parts[2]) else null
    }
  }
}
