package de.tfelix.bestia.worldgen.map

import de.tfelix.bestia.worldgen.random.NoiseVector
import java.io.Serializable
import java.util.*

/**
 * The map data part bundles map noise vectors with the map coordinates. It is
 * the main class upon which the workloads will be executed as a database.
 * Therefore it exposes an appropriate API.
 *
 * @author Thomas Felix
 */
data class MapDataPart(
    /**
     * Gives a unique identification string for this specific map part.
     *
     * @return The unique map part identification.
     */
    val ident: String,

    /**
     * @return The underlying map part of this map data.
     */
    val mapChunk: MapChunk
) : Serializable {

  private val noiseVectors = mutableMapOf<MapCoordinate, NoiseVector>()

  init {
    if (ident.isEmpty()) {
      throw IllegalArgumentException("Ident can not be empty of null.")
    }
  }

  /**
   * Adds the generated noise vector associated to a map coordiante to this
   * [MapDataPart].
   *
   * @param cord
   * A coordinate on this map.
   * @param vec
   * A noise vector associated with this part.
   */
  fun addCoordinateNoise(cord: MapCoordinate, vec: NoiseVector) {

    Objects.requireNonNull(cord)
    Objects.requireNonNull(vec)

    noiseVectors[cord] = vec
  }

  /**
   * Returns the [NoiseVector] for the given map coordiante. Or null
   * if this coordinate is not part of the [MapDataPart].
   *
   * @param cord The coordiante to get the related noise vector.
   */
  fun getCoordinateNoise(cord: MapCoordinate): NoiseVector {
    return noiseVectors[cord] ?: throw java.lang.IllegalArgumentException("No noise vector with coordinate $cord found")
  }

  override fun toString(): String {
    return "MapDataPart[ident: $ident]"
  }
}
