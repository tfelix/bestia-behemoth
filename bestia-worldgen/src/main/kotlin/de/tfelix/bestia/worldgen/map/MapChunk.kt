package de.tfelix.bestia.worldgen.map

import java.io.Serializable

/**
 * Map parts describe some discrete unit of a map. This chunks of the map must
 * be somehow be countable in order to create random data from it. Implementing
 * this interface describes certain maps.
 *
 * @author Thomas Felix
 */
interface MapChunk : Serializable {

  /**
   * Iterator to iterate over all the contained map coordinates contained in
   * this bunch of map..
   *
   * @return The iterator to iterate all over the contained
   * [MapCoordinate].
   */
  val iterator: Iterator<MapCoordinate>

  /**
   * Returns an identifier for this specific part of the map.
   *
   * @return A unique identifier for this specific part of the map.
   */
  val ident: String

  /**
   * Returns the total size/number of [MapCoordinate] inside this map
   * part.
   *
   * @return Total size of this [MapChunk].
   */
  fun size(): Long

  fun toGlobalCoordinates(localCords: MapCoordinate): MapCoordinate
}
