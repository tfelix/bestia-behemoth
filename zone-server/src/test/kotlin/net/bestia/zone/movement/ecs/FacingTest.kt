package net.bestia.zone.movement.ecs

import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FacingTest {

  @Test
  fun `behind is the half the entity turns its back on`() {
    val facing = Facing()
    facing.turnTowards(Vec3L(0, 0, 0), Vec3L(5, 0, 0))

    assertTrue(facing.isBehind(Vec3L(0, 0, 0), Vec3L(-1, 0, 0)))
    assertFalse(facing.isBehind(Vec3L(0, 0, 0), Vec3L(1, 0, 0)))
  }

  @Test
  fun `turning towards the spot it stands on keeps the old facing`() {
    val facing = Facing(dx = 1, dy = 0)
    facing.turnTowards(Vec3L(2, 2, 0), Vec3L(2, 2, 0))

    assertTrue(facing.dx == 1 && facing.dy == 0)
  }
}
