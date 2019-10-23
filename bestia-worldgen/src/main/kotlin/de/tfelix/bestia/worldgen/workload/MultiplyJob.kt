package de.tfelix.bestia.worldgen.workload

import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.MapCoordinate
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.NoiseVector

/**
 * This job multiplies all noise values inside a given noise vector with the
 * provided value.
 *
 * @author Thomas Felix
 */
class MultiplyJob(
    private val factor: Double,
    noiseName: String
) : Job() {

  private val noiseNames = mutableSetOf(noiseName)

  override fun foreachNoiseVector(dao: MapGenDAO, data: MapDataPart, vec: NoiseVector, cord: MapCoordinate) {
    // Multiply all keys with this name.
    noiseNames.forEach { key ->
      val `val` = vec.getValueDouble(key)
      vec.setValue(key, `val` * factor)
    }
  }
}
