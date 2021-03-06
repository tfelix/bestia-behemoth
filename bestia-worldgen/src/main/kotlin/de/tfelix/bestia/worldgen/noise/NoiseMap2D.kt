package de.tfelix.bestia.worldgen.noise

import de.tfelix.bestia.worldgen.map.Point
import de.tfelix.bestia.worldgen.map.Size


class NoiseMap2D(
    val width: Int,
    val height: Int
) : Iterable<Pair<Point, Double>> {
  val data: Array<Array<Double>> = Array(width) {
    Array(height) { 0.0 }
  }

  val pointCount = width * height

  operator fun get(point: Point): Double {
    return data[point.x][point.y]
  }

  operator fun set(point: Point, value: Double) {
    data[point.x][point.y] = value
  }

  private inner class NoiseMapIterator() : Iterator<Pair<Point, Double>> {
    private var i = 0

    override fun hasNext(): Boolean {
      return i < width * height
    }

    override fun next(): Pair<Point, Double> {
      val x = i / width
      val y = i % height
      i++

      return Pair(Point(x, y, 0), data[x][y])
    }
  }

  override fun iterator(): Iterator<Pair<Point, Double>> {
    return NoiseMapIterator()
  }
}