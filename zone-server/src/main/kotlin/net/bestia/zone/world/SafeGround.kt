package net.bestia.zone.world

import net.bestia.zone.geometry.Vec3L

/** Where a body can be put down without landing in water. On the tick, where the voxels are. */
interface SafeGround {

  /** Whether the ground at ([x], [y]) stands above the sea and has no water on it. */
  fun isDry(x: Long, y: Long): Boolean

  /** The first home on offer, which is dry by construction; null when the world offers none. */
  fun dryHome(): Vec3L?

  /**
   * [target] itself, or a dry home when water has reached it since it was stored. With no dry home left,
   * standing up wet still beats not standing up.
   */
  fun dryOr(target: Vec3L): Vec3L {
    if (isDry(target.x, target.y)) {
      return target
    }
    return dryHome() ?: target
  }

  companion object {
    /** Every place is dry. Keeps tests of the consumers short. */
    val NONE = object : SafeGround {
      override fun isDry(x: Long, y: Long): Boolean {
        return true
      }

      override fun dryHome(): Vec3L? {
        return null
      }
    }
  }
}
