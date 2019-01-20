package net.bestia.model.map

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import net.bestia.model.geometry.Size

@Repository
interface MapDataRepository : CrudRepository<MapData, Long> {

  /**
   * Returns the size of this map.
   *
   * @return The size of the map.
   */
  @Query("SELECT new net.bestia.model.geometry.Size(MAX(md.x + md.width), MAX(md.y + md.height)) FROM MapData md")
  fun mapSize(): Size

  @Query("""FROM MapData md WHERE ((:x BETWEEN md.x AND md.x + md.width) AND (:y BETWEEN md.y AND md.y + md.height)) OR
     ((:x + :w BETWEEN md.x AND md.x + md.width) AND (:y BETWEEN md.y AND md.y + md.height)) OR
     ((:x BETWEEN md.x AND md.x + md.width) AND (:y + :h BETWEEN md.y AND md.y + md.height)) OR
     ((:x + :w BETWEEN md.x AND md.x + md.width) AND (:y + :h BETWEEN md.y AND md.y + md.height))""")
  fun findAllInRange(
      @Param("x") x: Long,
      @Param("y") y: Long,
      @Param("w") width: Long,
      @Param("h") height: Long
  ): List<MapData>
}
