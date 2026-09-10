package net.bestia.zone.world.settlement

import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs

/**
 * Where a building's door is, given the direction it faces.
 *
 * `BuildingChannels.DOOR_X`/`DOOR_Y` carry a **unit direction**, not a position, so the point has to be
 * derived. Projecting the bearing along the long axis is wrong for a third of the town: a dwelling stands
 * gable-to-street, but `TownStage` turns a temple, market, civic hall or warehouse broad-front, swapping the
 * footprint's `bearing` to the perpendicular while the door still faces the plot's street. That derivation
 * puts a priest inside the temple wall.
 *
 * Taking the extent in whichever direction the door actually faces is right for both, and stays right if
 * the generator ever stops turning buildings square to their plot.
 */
object DoorPoint {

  /**
   * Metres beyond the wall the doorstep sits.
   *
   * A building is a solid prop, not just a rectangle, so a point *on* the wall is inside the collider. One
   * metre clears it and stays on the pad, whose skirt only starts 1.5 m out.
   */
  const val STANDOFF_METRES = 1.0

  /** @return null when the door direction is degenerate, which a hand-built footprint can produce */
  fun of(footprint: FootprintFeature, doorBearing: Vec2d): Vec2d? {
    if (doorBearing.lengthSquared == 0.0) return null

    val facing = doorBearing.normalized()
    val across = footprint.bearing.perpendicular()
    val reach = abs(facing dot footprint.bearing) * footprint.halfLength +
      abs(facing dot across) * footprint.halfWidth

    return footprint.center + facing * (reach + STANDOFF_METRES)
  }
}
