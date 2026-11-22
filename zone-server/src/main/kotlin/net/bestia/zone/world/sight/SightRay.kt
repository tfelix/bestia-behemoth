package net.bestia.zone.world.sight

import net.bestia.worldgen.derived.Edges
import net.bestia.worldgen.derived.SightMask
import kotlin.math.abs
import kotlin.math.floor

/**
 * Where a chunk's [SightMask] comes from, by chunk coordinates. Primitive arguments, so a lookup boxes nothing.
 * Null means "not known": the ray treats that chunk as clear rather than building anything.
 */
fun interface SightMasks {
  fun maskOf(chunkX: Int, chunkY: Int, chunkZ: Int): SightMask?
}

/**
 * The line-of-sight walk: a 3D voxel traversal (Amanatides & Woo) from one entity's eyes to the other's chest.
 *
 * Runs on every attack and every spell, so it is written to be a few dozen array reads: no allocation per step,
 * the current chunk's mask kept until the ray leaves it, and an early answer for anything adjacent. A twelve-tile
 * shot visits about twenty voxels.
 *
 * What blocks: a voxel the mask marks opaque, and a structure wall on a border the ray crosses horizontally. The
 * two end voxels are never tested - the attacker's head and the target's chest are where they are.
 */
object SightRay {

  /** Eye height of the viewer above the voxel it stands in, in voxels. */
  const val EYE_HEIGHT = 1.5

  /** Where on the target the line aims, above the voxel it stands in. */
  const val AIM_HEIGHT = 1.0

  /**
   * Rays longer than this are not walked and count as clear. Every reach in the game is far shorter, and a caller
   * refuses an out-of-range target before it asks; this only keeps a bad caller from walking a thousand voxels.
   */
  const val MAX_STEPS = 512

  fun isClear(
    ax: Long, ay: Long, az: Long,
    bx: Long, by: Long, bz: Long,
    chunkSize: Int,
    chunkHeight: Int,
    masks: SightMasks
  ): Boolean {
    // Neighbours see each other: a melee swing never pays for a walk.
    if (abs(bx - ax) <= 1 && abs(by - ay) <= 1 && abs(bz - az) <= 1) return true

    val sx = ax + 0.5
    val sy = ay + 0.5
    val sz = az + EYE_HEIGHT
    val dx = (bx + 0.5) - sx
    val dy = (by + 0.5) - sy
    val dz = (bz + AIM_HEIGHT) - sz

    var vx = floor(sx).toLong()
    var vy = floor(sy).toLong()
    var vz = floor(sz).toLong()
    val endX = bx
    val endY = by
    val endZ = floor(bz + AIM_HEIGHT).toLong()

    val stepX = if (dx > 0) 1 else -1
    val stepY = if (dy > 0) 1 else -1
    val stepZ = if (dz > 0) 1 else -1

    val tDeltaX = if (dx != 0.0) abs(1.0 / dx) else Double.POSITIVE_INFINITY
    val tDeltaY = if (dy != 0.0) abs(1.0 / dy) else Double.POSITIVE_INFINITY
    val tDeltaZ = if (dz != 0.0) abs(1.0 / dz) else Double.POSITIVE_INFINITY

    var tMaxX = if (dx != 0.0) ((if (stepX > 0) vx + 1 else vx) - sx) / dx else Double.POSITIVE_INFINITY
    var tMaxY = if (dy != 0.0) ((if (stepY > 0) vy + 1 else vy) - sy) / dy else Double.POSITIVE_INFINITY
    var tMaxZ = if (dz != 0.0) ((if (stepZ > 0) vz + 1 else vz) - sz) / dz else Double.POSITIVE_INFINITY

    // The chunk the walk is in, and its mask; refetched only when the walk crosses into another.
    var chunkX = Math.floorDiv(vx, chunkSize.toLong()).toInt()
    var chunkY = Math.floorDiv(vy, chunkSize.toLong()).toInt()
    var chunkZ = Math.floorDiv(vz, chunkHeight.toLong()).toInt()
    var mask = masks.maskOf(chunkX, chunkY, chunkZ)

    var steps = 0
    while (steps++ < MAX_STEPS) {
      if (vx == endX && vy == endY && vz == endZ) return true

      // Which border the ray crosses next, and the wall standing on it if it is a horizontal one.
      val crossing: Int
      if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
        if (tMaxX > 1.0) return true
        crossing = if (stepX > 0) Edges.X_PLUS else Edges.X_MINUS
        if (mask != null && wallBlocks(mask, vx, vy, vz, chunkSize, chunkHeight, crossing)) return false
        vx += stepX
        tMaxX += tDeltaX
      } else if (tMaxY <= tMaxZ) {
        if (tMaxY > 1.0) return true
        crossing = if (stepY > 0) Edges.Y_PLUS else Edges.Y_MINUS
        if (mask != null && wallBlocks(mask, vx, vy, vz, chunkSize, chunkHeight, crossing)) return false
        vy += stepY
        tMaxY += tDeltaY
      } else {
        if (tMaxZ > 1.0) return true
        vz += stepZ
        tMaxZ += tDeltaZ
      }

      val cx = Math.floorDiv(vx, chunkSize.toLong()).toInt()
      val cy = Math.floorDiv(vy, chunkSize.toLong()).toInt()
      val cz = Math.floorDiv(vz, chunkHeight.toLong()).toInt()
      if (cx != chunkX || cy != chunkY || cz != chunkZ) {
        chunkX = cx
        chunkY = cy
        chunkZ = cz
        mask = masks.maskOf(cx, cy, cz)
      }

      if (vx == endX && vy == endY && vz == endZ) return true

      if (mask != null && mask.blocks(
          Math.floorMod(vx, chunkSize.toLong()).toInt(),
          Math.floorMod(vy, chunkSize.toLong()).toInt(),
          Math.floorMod(vz, chunkHeight.toLong()).toInt()
        )
      ) {
        return false
      }
    }

    return true
  }

  /**
   * A wall on the border of the voxel the ray is leaving. Walls are written into the cells on both sides of a
   * border, so the cell being left always knows about it, even when the next cell is in another chunk.
   */
  private fun wallBlocks(
    mask: SightMask,
    vx: Long, vy: Long, vz: Long,
    chunkSize: Int,
    chunkHeight: Int,
    crossing: Int
  ): Boolean {
    val walls = mask.wallsAt(
      Math.floorMod(vx, chunkSize.toLong()).toInt(),
      Math.floorMod(vy, chunkSize.toLong()).toInt(),
      Math.floorMod(vz, chunkHeight.toLong()).toInt()
    )

    return walls and crossing != Edges.NONE
  }
}
