package net.bestia.zone.ecs.spawn

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Flat 2D bucket index over wild dens: "which dens are near this point", answered with a few hash probes.
 *
 * ### Why not [AreaOfInterestService][net.bestia.zone.ecs.AreaOfInterestService]
 *
 * The octree is the right structure for arbitrary volumes over a population that moves. Dens are neither.
 * Two properties make this problem much smaller than the general one, and this index leans on both:
 *
 *  - **Dens never move.** `WildSpawnerService.dens` is a pure function of the world seed, resolved once and
 *    never mutated, and nothing destroys a den after boot. So the structure is built once at boot and only
 *    read afterwards - no re-homing, no rebalancing, no lock, no removal path.
 *  - **Activation is horizontal.** [SpawnerSystem.withinActivation] ignores z on purpose: a den and a player
 *    on the same hillside can be a hundred metres apart vertically and in plain sight of one another. So the
 *    key has no z axis, and a query is a column rather than a cube - which is exactly the shape the question
 *    has, and which a cube query would have had to fake with an artificial vertical extent.
 *
 * Dens are also deliberately kept out of the entity AOI index. That one is read by `PerceptionSystem`,
 * `GetAllEntitiesHandler`, `WorldObjectResidencyService` and the client's initial snapshot, and a den is not
 * a thing a fireball should hit or a client should ever hear about.
 *
 * ### The correctness condition
 *
 * [CELL_SIZE] must be at least [SpawnerSystem.MAX_ACTIVATION_RANGE], or the 3x3 neighbourhood [collectNear]
 * walks would not cover the radius the caller then tests against, and dens would be missed at the edges of
 * their range - silently, and only for some positions. Checked in [init] rather than left as a comment.
 */
@Service
class SpawnerCellIndex {

  private val buckets = HashMap<Long, MutableList<EntityId>>()

  init {
    require(CELL_SIZE >= SpawnerSystem.MAX_ACTIVATION_RANGE) {
      "CELL_SIZE $CELL_SIZE is smaller than MAX_ACTIVATION_RANGE ${SpawnerSystem.MAX_ACTIVATION_RANGE}, so " +
          "the 3x3 neighbourhood would not cover a den's activation radius"
    }
  }

  /** Dens indexed so far. Read at boot to check nothing was dropped between placement and indexing. */
  val size: Int get() = buckets.values.sumOf { it.size }

  /**
   * Records a den at [position]. Called once per den at boot and never again; there is no counterpart
   * removal because nothing destroys a den (see `WildSpawnerBootRunner`). Should that ever change, the
   * consequence here is a stale id that [SpawnerSystem]'s narrow phase drops when the component fetch comes
   * back null - a skipped candidate, not a crash.
   */
  fun add(id: EntityId, position: Vec3L) {
    buckets.getOrPut(keyOf(position.x, position.y)) { mutableListOf() }.add(id)
  }

  /**
   * Appends every den in the 3x3 cells around [center] into [into].
   *
   * Takes the destination rather than returning a set so a caller can union across several players without
   * allocating one collection per player. This is a broad phase: it over-reports by up to a cell in each
   * direction, and the caller is expected to follow with the exact distance test.
   */
  fun collectNear(center: Vec3L, into: MutableSet<EntityId>) {
    val cx = cell(center.x)
    val cy = cell(center.y)

    for (dx in -1..1) {
      for (dy in -1..1) {
        val bucket = buckets[key(cx + dx, cy + dy)] ?: continue
        into.addAll(bucket)
      }
    }
  }

  private fun keyOf(x: Long, y: Long): Long = key(cell(x), cell(y))

  /**
   * The cell a world coordinate falls in.
   *
   * Arithmetic shift, **not** `/ CELL_SIZE`. Integer division truncates towards zero, so -1 and +1 would
   * both land in cell 0 and that one cell would be twice as wide as every other. The world is centred on
   * the origin, so that would mis-bucket half of it - and only for dens, only near the axes, which is the
   * kind of wrong that never looks like a bug.
   */
  private fun cell(coordinate: Long): Long = coordinate shr CELL_SHIFT

  /** Two cell coordinates packed into one key, so a lookup is a single probe with nothing allocated. */
  private fun key(cellX: Long, cellY: Long): Long = (cellX shl 32) or (cellY and 0xFFFFFFFFL)

  companion object {
    private const val CELL_SHIFT = 10
    const val CELL_SIZE = 1L shl CELL_SHIFT
  }
}
