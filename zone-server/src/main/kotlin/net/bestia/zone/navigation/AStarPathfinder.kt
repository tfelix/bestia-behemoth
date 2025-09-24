package net.bestia.zone.navigation

import kotlin.math.abs

class AStarPathfinder {
  data class Vec2(val x: Int, val y: Int)

  private data class Node(
    val point: Vec2,
    val g: Int,
    val h: Int,
    val f: Int,
    val parent: Node?
  )

  fun findPath(navGrid: NavGrid, start: Vec2, goal: Vec2): List<Vec2>? {
    val openSet = mutableListOf<Node>()
    val closedSet = mutableSetOf<Vec2>()
    openSet.add(Node(start, 0, heuristic(start, goal), heuristic(start, goal), null))

    while (openSet.isNotEmpty()) {
      val current = openSet.minByOrNull { it.f }!!
      if (current.point == goal) {
        val path = mutableListOf<Vec2>()
        var node: Node? = current
        while (node != null) {
          path.add(0, node.point)
          node = node.parent
        }

        return path
      }
      openSet.remove(current)
      closedSet.add(current.point)

      val (x, y) = current.point
      val tile = navGrid.getTile(x, y) ?: continue

      val neighbors = mutableListOf<Vec2>()
      if (tile.canWalkLeft) neighbors.add(Vec2(x + 1, y))
      if (tile.canWalkRight) neighbors.add(Vec2(x - 1, y))
      if (tile.canWalkUp) neighbors.add(Vec2(x, y + 1))
      if (tile.canWalkDown) neighbors.add(Vec2(x, y - 1))

      for (neighbor in neighbors) {
        if (neighbor in closedSet) continue
        val neighborTile = navGrid.getTile(neighbor.x, neighbor.y) ?: continue
        val cost = current.g + 1 + abs(tile.height - neighborTile.height)
        val h = heuristic(neighbor, goal)
        val node = Node(neighbor, cost, h, cost + h, current)
        val existing = openSet.find { it.point == neighbor }
        if (existing == null || node.g < existing.g) {
          openSet.remove(existing)
          openSet.add(node)
        }
      }
    }

    return null
  }

  private fun heuristic(a: Vec2, b: Vec2): Int =
    abs(a.x - b.x) + abs(a.y - b.y)
}