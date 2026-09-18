package net.bestia.zone.world.ground

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Rows are fetched per column as players arrive, never all at once.
 *
 * The difference from `ScorchRepository`, which loads everything at boot: a world only ever holds a handful of
 * scars, and it can eventually hold a worn row for every column anyone has walked across. `findAll` here would
 * be a table scan that grows with the age of the shard.
 */
@Repository
interface GroundLayerMarkRepository : JpaRepository<GroundLayerMark, GroundLayerMark.Key> {

  fun findByIdColumnKey(columnKey: Long): List<GroundLayerMark>

  fun deleteByIdColumnKeyAndIdLayerId(columnKey: Long, layerId: Int)
}
