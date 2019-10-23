package de.tfelix.bestia.worldgen.workload

import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.MapCoordinate
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.NoiseVector

/**
 * Do define certain processing on workloads jobs are attached. Usually on
 * extends this class and implements the
 * [.foreachNoiseVector].
 *
 * @author Thomas Felix
 */
abstract class Job {

  internal fun execute(dao: MapGenDAO, data: MapDataPart) {
    val part = data.mapChunk
    val cords = part.iterator

    onStart()

    while (cords.hasNext()) {
      val cord = cords.next()
      val vec = data.getCoordinateNoise(cord)
      foreachNoiseVector(dao, data, vec, cord)
    }

    onFinish(dao, data)
  }

  abstract fun foreachNoiseVector(dao: MapGenDAO, data: MapDataPart, vec: NoiseVector, cord: MapCoordinate)

  /**
   * Called before the run starts, in case variables need to be initialized
   * this method can be used.
   */
  open fun onStart() {
    // no op.
  }

  /**
   * This method is called after the job has processed all [NoiseVector]
   * attached to this [MapDataPart].
   *
   * @param dao
   * The map generator DAO.
   * @param data
   * The map data part on which was operated.
   */
  open fun onFinish(dao: MapGenDAO, data: MapDataPart) {
    // no op.
  }
}
