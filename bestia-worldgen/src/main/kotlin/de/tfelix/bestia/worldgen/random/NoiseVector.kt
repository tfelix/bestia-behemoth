package de.tfelix.bestia.worldgen.random

import de.tfelix.bestia.worldgen.map.MapCoordinate
import mu.KotlinLogging
import java.io.Serializable

private val LOG = KotlinLogging.logger { }

/**
 * This class is used to determine how many dimensions the random map data should
 * contain. The vector will get initialized by the coordinate provider and then
 * fed into the map converter which will operate on this data.
 *
 * @author Thomas Felix
 */
class NoiseVector(
    /**
     * The [MapCoordinate] of this random vector.
     */
    private val coordinate: MapCoordinate,
    val values: MutableMap<String, Number> = mutableMapOf()
) : Serializable {

  fun getValueFloat(key: String): Float {
    return values[key]?.toFloat() ?: run {
      LOG.warn("Does not contain key: {}", key)
      0f
    }
  }

  fun getValueDouble(key: String): Double {
    return values[key]?.toDouble() ?: run {
      LOG.warn("Does not contain key: {}", key)
      0.0
    }
  }

  fun getValueInt(key: String): Int {
    return values[key]?.toInt() ?: run {
      LOG.warn("Does not contain key: {}", key)
      0
    }
  }

  fun getValueShort(key: String): Short {
    return values[key]?.toShort() ?: run {
      LOG.warn("Does not contain key: {}", key)
      0
    }.toShort()
  }

  /**
   * Sets the value of the vector for a specific point.
   *
   */
  fun setValue(key: String, data: Number) {
    values[key] = data
  }

  override fun toString(): String {
    return "NoiseVec[cord: $coordinate, data: $values]"
  }
}
