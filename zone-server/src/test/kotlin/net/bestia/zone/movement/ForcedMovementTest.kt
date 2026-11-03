package net.bestia.zone.movement

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.local.LocalWalkQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ForcedMovementTest {

  /** Flat ground with a wall on one column. */
  private class WallAt(private val wallX: Long) : LocalWalkQuery {
    override fun canStep(from: Vec3L, to: Vec3L): Boolean {
      return to.x != wallX
    }

    override fun surfaceAt(position: Vec3L): Long {
      return 0
    }

    override fun isResident(position: Vec3L): Boolean {
      return true
    }
  }

  private val world = testWorld()

  @Test
  fun `a dash stops next to its target`() {
    val id = world.createEntity { id -> add(id, Position(0, 0, 0)) }

    val moved = ForcedMovement(WallAt(wallX = 99)).towards(world, id, Vec3L(5, 0, 0), maxTiles = 10, keepDistance = 1)

    assertEquals(4, moved)
    assertEquals(Vec3L(4, 0, 0), world.getOrThrow(id, Position::class).toVec3L())
  }

  @Test
  fun `a knockback stops at a wall and ends the walk under way`() {
    val id = world.createEntity { id ->
      add(id, Position(0, 0, 0))
      add(id, Path(mutableListOf(Vec3L(1, 0, 0))))
    }

    val moved = ForcedMovement(WallAt(wallX = 3)).awayFrom(world, id, Vec3L(-1, 0, 0), tiles = 3)

    assertEquals(2, moved)
    assertEquals(Vec3L(2, 0, 0), world.getOrThrow(id, Position::class).toVec3L())
    assertFalse(world.has(id, Path::class))
  }
}
