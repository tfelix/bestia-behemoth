package net.bestia.zone.navigation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AStarPathfinderTest {
    @Test
    fun `findPath should find a straight path on flat grid`() {
        val width = 3
        val height = 3
        val tiles = Array(height) { y ->
            Array(width) { x ->
                Tile(
                    height = 0,
                    canWalkLeft = x < width - 1,
                    canWalkRight = x > 0,
                    canWalkUp = y < height - 1,
                    canWalkDown = y > 0
                )
            }
        }
        val navGrid = NavGrid(width, height, tiles)
        val pathfinder = AStarPathfinder()
        val start = AStarPathfinder.Vec2(0, 0)
        val goal = AStarPathfinder.Vec2(2, 0)
        val path = pathfinder.findPath(navGrid, start, goal)
        assertNotNull(path)
        assertEquals(listOf(
            AStarPathfinder.Vec2(0, 0),
            AStarPathfinder.Vec2(1, 0),
            AStarPathfinder.Vec2(2, 0)
        ), path)
    }

    @Test
    fun `findPath should return null if no path exists`() {
        val width = 2
        val height = 1
        val tiles = Array(height) { y ->
            Array(width) { x ->
                Tile(
                    height = 0,
                    canWalkLeft = false,
                    canWalkRight = false,
                    canWalkUp = false,
                    canWalkDown = false
                )
            }
        }
        val navGrid = NavGrid(width, height, tiles)
        val pathfinder = AStarPathfinder()
        val start = AStarPathfinder.Vec2(0, 0)
        val goal = AStarPathfinder.Vec2(1, 0)
        val path = pathfinder.findPath(navGrid, start, goal)
        assertNull(path)
    }

    @Test
    fun `findPath should handle height differences`() {
        val width = 2
        val height = 1
        val tiles = Array(height) { y ->
            Array(width) { x ->
                Tile(
                    height = x, // 0 and 1
                    canWalkLeft = x < width - 1,
                    canWalkRight = x > 0,
                    canWalkUp = false,
                    canWalkDown = false
                )
            }
        }
        val navGrid = NavGrid(width, height, tiles)
        val pathfinder = AStarPathfinder()
        val start = AStarPathfinder.Vec2(0, 0)
        val goal = AStarPathfinder.Vec2(1, 0)
        val path = pathfinder.findPath(navGrid, start, goal)
        assertNotNull(path)
        assertEquals(listOf(
            AStarPathfinder.Vec2(0, 0),
            AStarPathfinder.Vec2(1, 0)
        ), path)
    }
}

