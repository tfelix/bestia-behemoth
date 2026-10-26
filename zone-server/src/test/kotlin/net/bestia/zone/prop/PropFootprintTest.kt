package net.bestia.zone.prop

import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.test.assertEquals

class PropFootprintTest {

  /** Eight metres along, three across. */
  private val hall = StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 80, halfWidthDm = 30)

  @Test
  fun `standing inside the walls is no distance at all`() {
    assertEquals(0.0, distance(Vec3L(105, 101, 0), yaw = 0f))
  }

  @Test
  fun `distance is to the nearest wall, not to the centre`() {
    assertEquals(2.0, distance(Vec3L(110, 100, 0), yaw = 0f), 1e-9)
    assertEquals(2.0, distance(Vec3L(100, 105, 0), yaw = 0f), 1e-9)
  }

  @Test
  fun `a turned building turns its long side with it`() {
    // A quarter turn puts the eight-metre side on the y axis.
    assertEquals(2.0, distance(Vec3L(100, 110, 0), yaw = (PI / 2).toFloat()), 1e-6)
    assertEquals(2.0, distance(Vec3L(105, 100, 0), yaw = (PI / 2).toFloat()), 1e-6)
  }

  @Test
  fun `past a corner it is the diagonal`() {
    assertEquals(5.0, distance(Vec3L(111, 107, 0), yaw = 0f), 1e-9)
  }

  @Test
  fun `extents scale with the voxel size`() {
    val pose = PropPose(Vec3L(100, 100, 0), 0f)

    // Half-metre voxels: the eight metres along are sixteen units.
    assertEquals(4.0, PropFootprint.distanceTo(Vec3L(120, 100, 0), pose, hall, metresPerUnit = 0.5), 1e-9)
  }

  private fun distance(from: Vec3L, yaw: Float): Double {
    return PropFootprint.distanceTo(from, PropPose(Vec3L(100, 100, 0), yaw), hall, metresPerUnit = 1.0)
  }
}
