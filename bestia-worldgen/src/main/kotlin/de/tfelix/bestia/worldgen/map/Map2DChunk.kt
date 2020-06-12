package de.tfelix.bestia.worldgen.map

data class Map2DChunk(
    private val chunkPos: Point,
    override val width: Int,
    override val height: Int
) : Chunk {

  private inner class Map2DChunkIterator(
      private val scale: Int
  ) : Iterator<Point> {

    init {
      require(scale > 0) {
        "Scale must be bigger then 0"
      }
    }

    private var i: Int = 0
    private val total = (width / scale) * (height / scale)

    override fun hasNext(): Boolean {
      return i < total
    }

    override fun next(): Point {
      val curX = (i % width) * scale
      val curY = (i / height) * scale
      i += 1

      return Point(curX, curY, 0)
    }
  }

  override fun getIterator(scale: Int): Iterator<Point> {
    return Map2DChunkIterator(scale)
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