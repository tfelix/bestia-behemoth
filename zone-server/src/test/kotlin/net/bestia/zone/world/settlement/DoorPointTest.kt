package net.bestia.zone.world.settlement

import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.Vec2d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two building shapes `TownStage` produces, because they are the reason this derivation is not simply
 * `centre + bearing * halfLength`: a dwelling faces along its long axis and a temple faces across it.
 */
class DoorPointTest {

  @Test
  fun `a gable-to-street dwelling has its door on the short side`() {
    // Long axis east, door facing east: the short wall is halfLength away.
    val house = footprint(bearing = Vec2d(1.0, 0.0), halfLength = 8.0, halfWidth = 4.0)

    val door = DoorPoint.of(house, Vec2d(1.0, 0.0))!!

    assertEquals(8.0 + DoorPoint.STANDOFF_METRES, door.x, 1e-9)
    assertEquals(0.0, door.y, 1e-9)
    assertTrue(!house.contains(door.x, door.y), "the doorstep must be outside the walls")
  }

  @Test
  fun `a broad-front temple has its door on the long side`() {
    // `TownStage` swaps a civic building's bearing to the perpendicular while the door keeps facing the
    // street, so projecting along the long axis would put the caller inside the wall.
    val temple = footprint(bearing = Vec2d(0.0, 1.0), halfLength = 12.0, halfWidth = 5.0)

    val door = DoorPoint.of(temple, Vec2d(1.0, 0.0))!!

    assertEquals(5.0 + DoorPoint.STANDOFF_METRES, door.x, 1e-9)
    assertEquals(0.0, door.y, 1e-9)
    assertTrue(!temple.contains(door.x, door.y), "the doorstep must be outside the walls")
  }

  @Test
  fun `a door facing a corner clears both walls`() {
    val house = footprint(bearing = Vec2d(1.0, 0.0), halfLength = 8.0, halfWidth = 4.0)

    val door = DoorPoint.of(house, Vec2d(1.0, 1.0))!!

    assertTrue(!house.contains(door.x, door.y), "the doorstep must be outside the walls")
  }

  @Test
  fun `an unnormalised door bearing gives the same point as a normalised one`() {
    val house = footprint(bearing = Vec2d(1.0, 0.0), halfLength = 8.0, halfWidth = 4.0)

    val unit = DoorPoint.of(house, Vec2d(1.0, 0.0))!!
    val long = DoorPoint.of(house, Vec2d(37.0, 0.0))!!

    assertEquals(unit.x, long.x, 1e-9)
    assertEquals(unit.y, long.y, 1e-9)
  }

  @Test
  fun `a degenerate door bearing has no door rather than a NaN one`() {
    val house = footprint(bearing = Vec2d(1.0, 0.0), halfLength = 8.0, halfWidth = 4.0)

    assertNull(DoorPoint.of(house, Vec2d(0.0, 0.0)))
  }

  private fun footprint(bearing: Vec2d, halfLength: Double, halfWidth: Double): FootprintFeature {
    return FootprintFeature(
      id = FeatureId(1L),
      kind = FeatureKind.BUILDING,
      center = Vec2d(0.0, 0.0),
      bearing = bearing,
      halfLength = halfLength,
      halfWidth = halfWidth
    )
  }
}
