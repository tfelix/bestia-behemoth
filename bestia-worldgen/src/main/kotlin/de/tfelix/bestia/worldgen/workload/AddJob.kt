package de.tfelix.bestia.worldgen.workload

import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.MapCoordinate
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.NoiseVector

/**
 * This job adds a static offset value to the returned noise.
 *
 * @author Thomas Felix
 */
class AddJob(
    private val offset: Double,
    private val noiseName: String
) : Job() {

  override fun foreachNoiseVector(dao: MapGenDAO, data: MapDataPart, vec: NoiseVector, cord: MapCoordinate) {
    val value = vec.getValueDouble(noiseName)
    vec.setValue(noiseName, value + offset)
  }
}
