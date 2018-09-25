package net.bestia.model.dao

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

import net.bestia.model.domain.MapParameter

/**
 * Simple DAO object for accessing the map parameter data.
 *
 * @author Thomas Felix
 */
@Repository("mapParameterDao")
interface MapParameterDAO : CrudRepository<MapParameter, Int> {

  /**
   * Returns the latest [MapParameter] from the database.
   *
   * @return The latest [MapParameter].
   */
  fun findFirstByOrderByIdDesc(): MapParameter?
}
