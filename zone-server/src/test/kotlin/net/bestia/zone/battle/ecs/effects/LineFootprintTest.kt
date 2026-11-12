package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LineFootprintTest {

  @Test
  fun `a wall cast northwards runs east to west across the centre`() {
    val wall = LineFootprint.across(caster = Vec3L(0, 0, 0), centre = Vec3L(0, 5, 0), length = 5)

    assertTrue(wall.covers(-2, 5))
    assertTrue(wall.covers(2, 5))
    assertFalse(wall.covers(0, 6), "the wall is one tile thick")
    assertFalse(wall.covers(3, 5), "five tiles long")
  }

  @Test
  fun `a wall cast on the diagonal lists its tiles across the other diagonal`() {
    val wall = LineFootprint.across(caster = Vec3L(0, 0, 0), centre = Vec3L(4, 4, 0), length = 3)

    assertEquals(listOf(Vec3L(5, 3, 0), Vec3L(4, 4, 0), Vec3L(3, 5, 0)), wall.tiles())
  }
}
