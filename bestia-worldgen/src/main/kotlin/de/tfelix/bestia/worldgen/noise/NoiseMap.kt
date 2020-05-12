package de.tfelix.bestia.worldgen.noise

import de.tfelix.bestia.worldgen.map.Point
import java.lang.IllegalArgumentException

data class NoiseMap(
    private val data: MutableMap<Point, Double>
) {

  operator fun get(point: Point): Double {
    return data[point]
        ?: throw IllegalArgumentException("$point does not exist in this NoiseMap")
  }

  operator fun set(point: Point, value: Double) {
    data[point] = value
  }
}