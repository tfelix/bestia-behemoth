package de.tfelix.bestia.worldgen.map

data class Map2DChunk(
    private val point: Point,
    override val width: Int,
    override val height: Int
) : Chunk {

  private inner class Map2DChunkIterator : Iterator<Point> {
    private var i: Int = 0

    override fun hasNext(): Boolean {
      return i < width * height
    }

    override fun next(): Point {
      val curX = i % width
      val curY = i / height
      i += 1

      return Point(curX, curY, 0)
    }
  }

  override fun getIterator(): Iterator<Point> {
    return Map2DChunkIterator()
  }

  override val identifier = "chunk-${point.x}-${point.y}-${point.z}"
}