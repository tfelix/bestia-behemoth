package de.tfelix.bestia.worldgen.map

import kotlin.math.sqrt

data class Point(
    val x: Int,
    val y: Int,
    val z: Int
) {

  fun distance(p: Point): Double {
    val d = this - p
    return sqrt((d.x * d.x + d.y * d.y + d.z * d.z).toDouble())
  }

  operator fun minus(rhs: Point): Point {
    return Point(x - rhs.x, y - rhs.y, z - rhs.z)
  }
  
  operator fun plus(rhs: Point): Point {
    return Point(x + rhs.x, y + rhs.y, z + rhs.z)
  }
}