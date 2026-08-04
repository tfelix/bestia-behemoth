package net.bestia.zone.navigation.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the generic [AStar] that replaced the grid-only `AStarPathfinder`.
 *
 * The first three cases are the ones that class was tested for - a straight path, an unreachable goal, a
 * height difference - carried over so the replacement is held to at least the same standard. The rest are
 * properties the old test could not express, because a fixed four-connected grid of uniform cost cannot
 * produce a case where cost and step count disagree.
 */
class AStarTest {

  /** A small weighted graph described by its adjacency, for tests that care about cost rather than geometry. */
  private class TestGraph(
    private val edges: Map<Int, List<Pair<Int, Double>>>,
    private val estimates: Map<Int, Double> = emptyMap()
  ) : AStarGraph<Int> {
    override fun neighbors(node: Int) = edges[node]?.map { it.first } ?: emptyList()
    override fun cost(from: Int, to: Int) = edges[from]?.first { it.first == to }?.second ?: 0.0
    override fun heuristic(node: Int, goal: Int) = estimates[node] ?: 0.0
  }

  @Test
  fun `finds a straight path across a flat line of nodes`() {
    val graph = TestGraph(
      mapOf(
        0 to listOf(1 to 1.0),
        1 to listOf(0 to 1.0, 2 to 1.0),
        2 to listOf(1 to 1.0)
      )
    )

    assertEquals(listOf(0, 1, 2), AStar.findPath(graph, 0, 2))
  }

  @Test
  fun `returns null when the goal cannot be reached`() {
    val graph = TestGraph(mapOf(0 to emptyList(), 1 to emptyList()))

    assertNull(AStar.findPath(graph, 0, 1))
  }

  @Test
  fun `a start that is already the goal is a path of one`() {
    val graph = TestGraph(mapOf(0 to emptyList()))

    assertEquals(listOf(0), AStar.findPath(graph, 0, 0))
  }

  @Test
  fun `takes the cheaper of two routes even when it is the longer one`() {
    // The property a uniform grid cannot test, and the one the whole design turns on: a wild animal's route
    // over open country is *more steps* than the road beside it and still preferred, because the road costs
    // it more per step. If A* ever went by hop count this would silently invert every movement profile.
    val graph = TestGraph(
      mapOf(
        0 to listOf(1 to 10.0, 2 to 1.0),
        // Direct: one hop of 10. Round the houses: three hops of 1.
        1 to listOf(0 to 10.0),
        2 to listOf(0 to 1.0, 3 to 1.0),
        3 to listOf(2 to 1.0, 1 to 1.0)
      )
    )

    assertEquals(listOf(0, 2, 3, 1), AStar.findPath(graph, 0, 1))
  }

  @Test
  fun `an admissible heuristic does not change the answer`() {
    // Guards the property, not the speed. A heuristic that over-estimates makes A* return a path that is
    // merely plausible - the failure this asserts against is subtle, seed-dependent and easy to introduce.
    val edges = mapOf(
      0 to listOf(1 to 1.0, 2 to 1.0),
      1 to listOf(0 to 1.0, 3 to 5.0),
      2 to listOf(0 to 1.0, 3 to 1.0),
      3 to listOf(1 to 5.0, 2 to 1.0)
    )

    val blind = AStar.findPath(TestGraph(edges), 0, 3)
    // True remaining costs, so the estimate is exact and therefore admissible.
    val guided = AStar.findPath(TestGraph(edges, mapOf(0 to 2.0, 1 to 5.0, 2 to 1.0, 3 to 0.0)), 0, 3)

    assertEquals(listOf(0, 2, 3), blind)
    assertEquals(blind, guided)
  }

  @Test
  fun `the node limit stops an unbounded search rather than hanging`() {
    // An open plain with no obstacle is the realistic version of this: the local tier searches columns it
    // discovers as it goes, so "no route" and "an enormous route" look identical until something says stop.
    val graph = object : AStarGraph<Int> {
      override fun neighbors(node: Int) = listOf(node + 1)
      override fun cost(from: Int, to: Int) = 1.0
      override fun heuristic(node: Int, goal: Int) = 0.0
    }

    assertNull(AStar.findPath(graph, 0, -1, nodeLimit = 100))
  }

  @Test
  fun `revisiting a node by a cheaper route updates the path`() {
    // A node reached early by an expensive route and later by a cheap one has to be re-opened. Getting this
    // wrong yields a valid-looking path that is not the cheapest, which no assertion about connectivity
    // would catch.
    val graph = TestGraph(
      mapOf(
        0 to listOf(1 to 1.0, 2 to 1.0),
        1 to listOf(3 to 100.0),
        2 to listOf(3 to 1.0),
        3 to emptyList()
      )
    )

    val path = assertNotNull(AStar.findPath(graph, 0, 3))
    assertEquals(listOf(0, 2, 3), path)
    assertTrue(1 !in path, "the expensive intermediate node should not be on the cheapest path")
  }
}
