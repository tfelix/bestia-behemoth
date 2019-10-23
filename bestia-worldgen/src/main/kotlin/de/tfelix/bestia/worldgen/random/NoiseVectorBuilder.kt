package de.tfelix.bestia.worldgen.random

import java.util.HashMap

import de.tfelix.bestia.worldgen.map.MapCoordinate

/**
 * This class is used to determine how many dimensional the random map data
 * should be. The vector will get initialized by the coordinate provider and
 * then fed into the map converter.
 *
 * @author Thomas Felix <thomas.felix></thomas.felix>@tfelix.de>
 */
class NoiseVectorBuilder {

  private val dimensions = HashMap<String, Tuple>()

  private data class Tuple(
      val provider: NoiseProvider,
      val type: Class<out Number>
  ) {
    override fun toString(): String {
      return "$provider => ${type.simpleName}"
    }
  }

  /**
   * Adds a new dimension to this builder. For each map coordinate the given
   * random dimension is created. See this as kind of a layer of random noise.
   * Later the workload algorithms can request this layer data and perform map
   * generation operations upon them.
   *
   * @param name
   * The name of the dimension. Later used to retrieve it again
   * from the system (layer name).
   * @param type
   * The type of the random data to be generated. Keep it as small
   * as possible (byte wise) to consume less memory.
   * @param provider
   * The provider which will generate the random data.
   */
  fun addDimension(name: String, type: Class<out Number>, provider: NoiseProvider) {
    dimensions[name] = Tuple(provider, type)
  }

  /**
   * Generates the a new random vector for the given map coordiantes.
   *
   * @param cord
   * The map coordinates to generate the random values for.
   * @return A new [NoiseVector] which contains all the random noise
   * seed data.
   */
  fun generate(cord: MapCoordinate): NoiseVector {

    val values = HashMap<String, Number>()

    for ((key, value1) in dimensions) {

      // Get the random value transform it to the needed value and save it
      // into the resulting vector.
      val value = cord.generate(value1.provider)

      when {
        value1.type == Float::class.java -> values[key] = value.toFloat()
        value1.type == Short::class.java -> values[key] = value.toShort()
        value1.type == Long::class.java -> values[key] = value.toLong()
        value1.type == Int::class.java -> values[key] = value.toInt()
        else -> values[key] = value
      }
    }

    return NoiseVector(cord, values)
  }

  override fun toString(): String {
    return "NoiseVectorBuilder[dimensions: $dimensions]"
  }
}
