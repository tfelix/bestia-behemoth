package de.tfelix.bestia.worldgen.description

import java.io.Serializable

import de.tfelix.bestia.worldgen.map.MapChunk
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder

/**
 * This is a entry point for the map generation algorithm. All methods are used
 * by the framework to get a basic understanding of the map generation process.
 *
 * @author Thomas Felix
 */
interface MapDescription : Serializable {

  /**
   * Returns the info how and which values the map part consumer will
   * need to generate the custom map.
   */
  val noiseVectorBuilder: NoiseVectorBuilder

  /**
   * Returns a iterator to generate all the map parts contained in this map.
   */
  val mapParts: Iterator<MapChunk>

  /**
   * The number of map parts returned by this iterator. It is made this way
   * because the map part count can be really really high so it is likley that
   * the [MapChunk]s are created on the fly. To know their count an extra
   * method is needed.
   *
   * @return The number of [MapChunk]s describing this map.
   */
  val mapPartCount: Long
}
