package net.bestia.zoneserver.map.path

import mu.KotlinLogging
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * Implements the A* pathfinding algorithm.
 *
 * @author Thomas Felix
 */
class AStarPathfinder<T>(
    private val nodeProvider: NodeProvider<T>,
    private val estimator: HeuristicEstimator<T>,
    /**
     * Maximum number of iterations in order to prevent extensive search on big
     * maps.
     */
    private val maxIteration: Int = 1000
) : Pathfinder<T> {

  private var openSet: PriorityQueue<Node<T>> = PriorityQueue()
  private val closedSet = HashSet<Node<T>>()

  init {
    if (maxIteration <= 0) {
      throw IllegalArgumentException("MaxIterations must be greater then 0.")
    }
  }

  override fun findPath(start: Node<T>, end: Node<T>): List<Node<T>> {
    LOG.trace("Finding path from {} to {}.", start, end)

    this.openSet = PriorityQueue(50, Comparator { a, b ->
      val targetDistA = estimator.getDistance(a.self, end.self)
      val targetDistB = estimator.getDistance(a.self, end.self)

      val dA = a.nodeCost + targetDistA
      val dB = b.nodeCost + targetDistB

      // Tie breaker.
      if (Math.abs(dA - dB) < 0.00001f) {
        return@Comparator 0
      }

      if (dA < dB) -1 else 1
    })

    var lastNode: Node<T>?
    var currentNode: Node<T>? = null
    openSet.add(start)

    // We must step into the loop at least once for the first tile. At first
    // step the openSet will be empty.
    var i = 0
    while (!openSet.isEmpty() && ++i <= maxIteration) {

      lastNode = currentNode
      currentNode = openSet.remove()

      // Add all neighboring nodes to open set.
      val connections = nodeProvider.getConnectedNodes(currentNode!!)
      LOG.trace("Node {} connections; {}.", currentNode, connections)
      connections.filter { c -> !closedSet.contains(c) }.forEach { openSet.add(it) }

      currentNode.parent = lastNode
      closedSet.add(currentNode)

      // Check if we already found the solution.
      // We can reconstruct the path then.
      if (currentNode == end) {
        val solution = ArrayList<Node<T>>()

        while (currentNode != null) {
          solution.add(currentNode)
          currentNode = currentNode.parent
        }

        // Path is now in reverse order. Fix this.
        solution.reverse()

        return solution
      }
    }

    return emptyList()
  }
}

