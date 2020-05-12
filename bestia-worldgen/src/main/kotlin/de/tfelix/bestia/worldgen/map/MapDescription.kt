package de.tfelix.bestia.worldgen.map

import java.io.Serializable

/**
 * This is a entry point for the map generation algorithm. All methods are used
 * by the framework to get a basic understanding of the map generation process.
 *
 * @author Thomas Felix
 */
interface MapDescription : Serializable {

  /**
   * Returns a iterator to generate all the map parts contained in this map.
   */
  fun getChunkIterator(): Iterator<Chunk>

  /**
   * The number of map parts returned by this iterator. It is made this way
   * because the map part count can be really really high so it is likely that
   * the [MapChunk]s are created on the fly. To know their count an extra
   * method is needed.
   *
   * @return The number of [MapChunk]s describing this map.
   */
  val chunkCount: Long
}
