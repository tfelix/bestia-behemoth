package net.bestia.zoneserver.map.path

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.geometry.Point
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

/**
 * Testing on a map like the following:
 *
 * <pre>
 * 0 x x
 * 0 x x
 * 0 x x
</pre> *
 *
 * @author Thomas
 */
@RunWith(MockitoJUnitRunner::class)
class AStarPathfinderTest {

  private lateinit var finder: AStarPathfinder<Point>

  private val estimator = PointEstimator()

  @Mock
  private lateinit var provider: NodeProvider<Point>

  @Before
  fun setup() {

    val n1 = Node(Point(0, 1))
    val n2 = Node(Point(0, 2))

    whenever(provider.getConnectedNodes(any())).thenAnswer { invocation ->
      val arg = invocation.getArgument<Node<Point>>(0)

      when (arg) {
        START -> setOf(Point(0, 1))
        n1 -> setOf(Point(0, 0), Point(0, 2))
        n2 -> setOf(Point(0, 1))
        else -> emptySet()
      }
    }

    finder = AStarPathfinder(provider, estimator)
  }

  private fun setOf(vararg points: Point): Set<Node<Point>> {
    return points.map { Node(it) }.toSet()
  }

  @Test
  fun findPath_noWayExists_empty() {
    val path = finder.findPath(START, END_BLOCKED)
    Assert.assertEquals(0, path.size.toLong())
  }

  @Test
  fun findPath_sticksToMaxIteration_empty() {
    finder = AStarPathfinder(provider, estimator, 1)

    val path = finder.findPath(START, END).asSequence().map{ it.self }.toList()

    Assert.assertEquals(0, path.size.toLong())
  }

  @Test
  fun findPath_wayExists_shortestPath() {
    val path = finder.findPath(START, END)
        .asSequence()
        .map { it.self }
        .toList()

    Assert.assertEquals(listOf(Point(0, 0), Point(0, 1), Point(0, 2)), path)
  }

  companion object {
    private val START = Node(Point(0, 0))
    private val END_BLOCKED = Node(Point(2, 2))
    private val END = Node(Point(0, 2))
  }
}
