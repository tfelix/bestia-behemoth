package net.bestia.model.map

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TilesetDataRepository : CrudRepository<TilesetData, Long> {

  /**
   * Returns the tilset which contains the tile with the given GID.
   *
   * @param gid
   * The GID to look for.
   * @return The [TilesetData] which contains this GID, or null if no
   * [TilesetData] was found.
   */
  @Query("SELECT t FROM TilesetData t WHERE t.minGid <= :gid AND t.maxGid >= :gid")
  fun findByGid(@Param("gid") gid: Long): TilesetData
}
