package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.geometry.Vec3L
import kotlin.math.abs
import kotlin.math.max

/**
 * A wall of tiles from [from] to [to], [halfWidth] tiles to either side of the line - a fire wall rather than a
 * square. The area's cube still decides which entities are asked, so it must be at least as large as the line.
 */
class LineFootprint(
  private val from: Vec3L,
  private val to: Vec3L,
  private val halfWidth: Long = 0,
) : AreaFootprint {

  override fun covers(voxelX: Long, voxelY: Long): Boolean {
    return tiles().any { tile -> abs(voxelX - tile.x) <= halfWidth && abs(voxelY - tile.y) <= halfWidth }
  }

  /** The tiles on the line itself, from [from] to [to], leaving out the width. */
  fun tiles(): List<Vec3L> {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val steps = max(abs(dx), abs(dy))
    if (steps == 0L) return listOf(from)

    return (0..steps).map { step -> Vec3L(from.x + dx * step / steps, from.y + dy * step / steps, from.z) }
  }

  companion object {
    /**
     * A wall of [length] tiles centred on [centre], running across the direction [caster] cast it from - the way
     * a fire wall is laid between the caster and what comes at them.
     */
    fun across(caster: Vec3L, centre: Vec3L, length: Int): LineFootprint {
      val dirX = (centre.x - caster.x).coerceIn(-1, 1)
      val dirY = (centre.y - caster.y).coerceIn(-1, 1)
      // Perpendicular to the direction of the cast; straight ahead when the caster stands on the centre.
      val (alongX, alongY) = if (dirX == 0L && dirY == 0L) 1L to 0L else -dirY to dirX
      val half = (length / 2).toLong()

      return LineFootprint(
        Vec3L(centre.x - alongX * half, centre.y - alongY * half, centre.z),
        Vec3L(centre.x + alongX * half, centre.y + alongY * half, centre.z),
      )
    }
  }
}
