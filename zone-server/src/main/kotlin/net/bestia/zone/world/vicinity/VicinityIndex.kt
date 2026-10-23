package net.bestia.zone.world.vicinity

import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Which entities are near each chunk column, and the reverse. Kept by [VicinitySystem].
 *
 * Tick thread only.
 */
@Service
class VicinityIndex {

  private val coverage = HashMap<EntityId, Coverage>()
  private val sensors = HashMap<ChunkColumn, MutableSet<EntityId>>()

  val size: Int
    get() {
      return coverage.size
    }

  /** Never a copy - do not mutate. */
  fun sensorsIn(column: ChunkColumn): Set<EntityId> {
    return sensors[column] ?: emptySet()
  }

  fun centerOf(entity: EntityId): ChunkColumn? {
    return coverage[entity]?.center
  }

  /**
   * Re-centres [entity]'s vicinity on [center], covering [columns].
   *
   * @return the columns it did not cover before
   */
  fun cover(entity: EntityId, center: ChunkColumn, columns: Set<ChunkColumn>): List<ChunkColumn> {
    val previous = coverage[entity]?.columns ?: emptySet()
    coverage[entity] = Coverage(center, columns)

    for (column in previous) {
      if (column !in columns) leave(entity, column)
    }

    val entered = columns.filter { it !in previous }
    entered.forEach { sensors.getOrPut(it) { HashSet() }.add(entity) }
    return entered
  }

  fun forgetAllBut(kept: Set<EntityId>) {
    val gone = coverage.keys.filter { it !in kept }

    for (entity in gone) {
      coverage.remove(entity)?.columns?.forEach { leave(entity, it) }
    }
  }

  private fun leave(entity: EntityId, column: ChunkColumn) {
    val near = sensors[column] ?: return
    near.remove(entity)
    if (near.isEmpty()) sensors.remove(column)
  }

  private class Coverage(val center: ChunkColumn, val columns: Set<ChunkColumn>)
}
