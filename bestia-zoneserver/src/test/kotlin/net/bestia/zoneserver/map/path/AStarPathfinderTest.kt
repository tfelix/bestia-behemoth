package net.bestia.zoneserver.map.path

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import net.bestia.model.geometry.Vec3
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

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
@ExtendWith(MockitoExtension::class)
class AStarPathfinderTest {

  private lateinit var finder: AStarPathfinder<Vec3>

  private val estimator = PointEstimator()

  @Mock
  private lateinit var provider: NodeProvider<Vec3>

  @BeforeEach
  fun setup() {

    val n1 = Node(Vec3(0, 1))
    val n2 = Node(Vec3(0, 2))

    whenever(provider.getConnectedNodes(any())).thenAnswer { invocation ->
      val arg = invocation.getArgument<Node<Vec3>>(0)

      when (arg) {
        START -> setOf(Vec3(0, 1))
        n1 -> setOf(Vec3(0, 0), Vec3(0, 2))
        n2 -> setOf(Vec3(0, 1))
        else -> emptySet()
      }
    }

    finder = AStarPathfinder(provider, estimator)
  }

  private fun setOf(vararg points: Vec3): Set<Node<Vec3>> {
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

    Assert.assertEquals(listOf(Vec3(0, 0), Vec3(0, 1), Vec3(0, 2)), path)
  }

  companion object {
    private val START = Node(Vec3(0, 0))
    private val END_BLOCKED = Node(Vec3(2, 2))
    private val END = Node(Vec3(0, 2))
  }
}
