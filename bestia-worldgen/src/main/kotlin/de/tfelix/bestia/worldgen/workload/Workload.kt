package de.tfelix.bestia.worldgen.workload

import de.tfelix.bestia.worldgen.io.MapGenDAO

/**
 * The [Workload] describes a unit of work to be done to the map. Usually
 * this are different operations. You either operate on the noise data, the
 * generated map data or you perform a transformation from one format to
 * another.
 *
 * @author Thomas Felix
 */
class Workload(
    val label: String
) {
  val jobs = mutableListOf<Job>()

  /**
   * This executes all previously added jobs to this workload.
   *
   * @param dao
   * The DAO to access data from the map creation process.
   */
  fun execute(dao: MapGenDAO) {
    val it = dao.mapDataPartIterator

    while (it.hasNext()) {
      val part = it.next()
      jobs.forEach { job -> job.execute(dao, part) }
    }
  }
}
