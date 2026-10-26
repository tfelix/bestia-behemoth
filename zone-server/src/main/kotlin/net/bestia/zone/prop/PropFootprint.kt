package net.bestia.zone.prop

import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Horizontal distance from a point to a static prop's walls, in position units, and zero inside them.
 *
 * Measured to the walls rather than to the centre, because a market hall is sixteen metres long and a player
 * standing at its gable is as close to it as one standing at its door.
 */
object PropFootprint {

  fun distanceTo(from: Vec3L, pose: PropPose, visual: StaticVisual, metresPerUnit: Double): Double {
    val dx = (from.x - pose.position.x).toDouble()
    val dy = (from.y - pose.position.y).toDouble()

    // Into the prop's own frame. The generator's yaw is the bearing of the long axis.
    val along = dx * cos(pose.yaw) + dy * sin(pose.yaw)
    val across = -dx * sin(pose.yaw) + dy * cos(pose.yaw)

    val outsideAlong = max(abs(along) - unitsOf(visual.halfLengthDm, metresPerUnit), 0.0)
    val outsideAcross = max(abs(across) - unitsOf(visual.halfWidthDm, metresPerUnit), 0.0)

    return hypot(outsideAlong, outsideAcross)
  }

  private fun unitsOf(decimetres: Int, metresPerUnit: Double): Double {
    return decimetres / 10.0 / metresPerUnit
  }
}
