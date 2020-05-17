package de.tfelix.bestia.worldgen.map

data class Map2DChunk(
    private val chunkPos: Point,
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

  override fun toGlobalCoordinates(localCords: Point): Point {
    return Point(
        chunkPos.x * width + localCords.x,
        chunkPos.y * height + localCords.y,
        0
    )
  }

  override val identifier = "chunk-${chunkPos.x}-${chunkPos.y}-${chunkPos.z}"
}