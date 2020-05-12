package de.tfelix.bestia.worldgen.map

/**
 * This map info implementation describes a discrete two dimensional map
 * usable for tilemap creation.
 *
 * @author Thomas Felix
 */
data class Map2DDescription(
    val mapWidth: Long = 0,
    val mapHeight: Long = 0,
    val chunkWidth: Long = 0,
    val chunkHeight: Long = 0
) : MapDescription {

  private val chunksX = mapWidth / chunkWidth
  private val chunksY = mapHeight / chunkHeight

  override fun getChunkIterator(): Iterator<Chunk> {
    return Map2DIterator()
  }

  override val chunkCount: Long
    get() {
      val parts = chunksX * chunksY
      return if (parts == 0L) 1 else parts
    }

  private inner class Map2DIterator : Iterator<Chunk> {
    private var i: Long = 0

    override fun hasNext(): Boolean {
      return i < chunkCount
    }

    override fun next(): Chunk {
      val curX = i % chunkWidth
      val curY = i / chunkHeight
      i += 1

      return Map2DChunk(
          Point(curX, curY, 0),
          chunkWidth,
          chunkHeight
      )
    }
  }
}
