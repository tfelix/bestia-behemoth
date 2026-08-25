package net.bestia.zone.world.fire

import net.bestia.zone.util.EntityId

/**
 * One fire: which cells are alight, how long each has been, and the effect entity hurting whatever stands in
 * them.
 *
 * ### Not an entity itself
 *
 * A fire has no position of its own, no visual, and no health, and it spans chunks - so it is state a service
 * holds rather than a row in the ECS. Contrast `AreaEffect`, which *is* an entity precisely because it has an
 * `EntityVisual` and a place to be. The fire's appearance is the burning mask on the wire, and the entity
 * below exists only to carry damage.
 *
 * ### Cells are keyed globally, not per chunk
 *
 * A fire crossing a chunk boundary is the ordinary case, not the exception, so a per-chunk structure would
 * make the common path the awkward one. `(voxelX, voxelY)` packed into a `Long` is the same trick
 * `ScorchRegistry` uses for columns, one lattice down.
 */
class GroundFire(
  val id: Long,
  val casterId: EntityId,
  val skillId: Long,
  val skillLevel: Int,
  /** Bestia second the fire started, stamped onto every column it scars. See [ScorchMark]. */
  val startedAtSecond: Long,
) {

  /** Packed `(voxelX, voxelY)` to how many steps that cell has been alight. */
  val burning = HashMap<Long, Int>()

  /** The invisible [net.bestia.zone.ecs.battle.effects.AreaEffect] entity covering the front, if one is up. */
  var effectId: EntityId? = null

  var sinceLastStep: Float = 0f
  var sinceLastDamage: Float = 0f
  var ageSeconds: Float = 0f

  /** How many cells this fire has ever set alight, for the per-fire cap. */
  var everIgnited: Int = 0
    private set

  var minX: Long = Long.MAX_VALUE
    private set
  var minY: Long = Long.MAX_VALUE
    private set
  var maxX: Long = Long.MIN_VALUE
    private set
  var maxY: Long = Long.MIN_VALUE
    private set

  val isOut get() = burning.isEmpty()

  fun ignite(voxelX: Long, voxelY: Long) {
    if (burning.putIfAbsent(pack(voxelX, voxelY), 0) != null) return

    if (originX == Long.MIN_VALUE) {
      originX = voxelX
      originY = voxelY
    }

    everIgnited++
    minX = minOf(minX, voxelX)
    minY = minOf(minY, voxelY)
    maxX = maxOf(maxX, voxelX)
    maxY = maxOf(maxY, voxelY)
  }

  fun isBurning(voxelX: Long, voxelY: Long) = burning.containsKey(pack(voxelX, voxelY))

  /**
   * Where the fire was lit, and where its damage cube stays anchored.
   *
   * The cube does not follow the front, because it does not need to: the bounding box **never shrinks**, so a
   * box grown around the ignition point always covers every burning cell. Keeping it still is also what lets
   * `GroundFireSystem` declare `AreaEffect` alone rather than conflicting with the position store - see
   * `GroundFireDamage`.
   */
  var originX: Long = Long.MIN_VALUE
    private set
  var originY: Long = Long.MIN_VALUE
    private set

  /**
   * Half-extent from [originX]/[originY] out to the furthest cell this fire has reached, in tiles.
   *
   * Chebyshev rather than Euclidean, because it sizes a cube.
   */
  val radiusFromOrigin: Long
    get() = maxOf(
      maxOf(maxX - originX, originX - minX),
      maxOf(maxY - originY, originY - minY)
    ).coerceAtLeast(1)

  /** The bounding box centre, for asking the weather where this fire is. */
  val centreX get() = (minX + maxX) / 2
  val centreY get() = (minY + maxY) / 2

  companion object {
    fun pack(voxelX: Long, voxelY: Long): Long = (voxelX shl 32) or (voxelY and 0xFFFFFFFFL)

    fun unpackX(packed: Long): Long = packed shr 32

    /** Sign-extended, because a voxel y south of the origin is negative. */
    fun unpackY(packed: Long): Long = (packed and 0xFFFFFFFFL).toInt().toLong()
  }
}
