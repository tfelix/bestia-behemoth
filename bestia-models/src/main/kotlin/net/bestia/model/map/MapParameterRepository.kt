package net.bestia.model.map

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MapParameterRepository : CrudRepository<MapParameter, Long> {

  /**
   * Returns the latest [MapParameter] from the database.
   *
   * @return The latest [MapParameter].
   */
  fun findFirstByOrderByIdDesc(): MapParameter?
}
