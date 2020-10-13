package net.bestia.zoneserver.integration

import net.bestia.zoneserver.map.path.Connection
import net.bestia.zoneserver.map.path.NodeRepository
import net.bestia.zoneserver.map.path.Waypoint
import org.junit.Assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.neo4j.graphalgo.*
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.PathExpanders
import org.springframework.beans.factory.annotation.Autowired
import kotlin.math.pow
import kotlin.math.sqrt

@IntegrationTest
class AStarTest {

  @Autowired
  private lateinit var graphDb: GraphDatabaseService

  @Autowired
  private lateinit var nodeRepository: NodeRepository

  @Test
  fun test() {
    val estimateEvaluator: EstimateEvaluator<Double> = EstimateEvaluator<Double> { node, goal ->
      val dx = node.getProperty("x") as Double - goal.getProperty("x") as Double
      val dy = node.getProperty("y") as Double - goal.getProperty("y") as Double
      sqrt(dx.pow(2.0) + dy.pow(2.0))
    }

    val n1 = nodeRepository.findNodeById(w1)
    val n2 = nodeRepository.findNodeById(w2)

    println(graphDb.databaseName())
    // graphDb.beginTx().use { tx ->
      /*val astar: PathFinder<WeightedPath> = GraphAlgoFactory.aStar(BasicEvaluationContext(tx, graphDb),
          PathExpanders.allTypesAndDirections<Any>(),
          CommonEvaluators.doubleCostEvaluator("length"), estimateEvaluator)

      val path: WeightedPath = astar.findSinglePath(n1, n2)*/

      // println(path)
    // }
  }

  companion object {
    private var w1 = 0L
    private var w2 = 0L

    @BeforeAll
    @JvmStatic
    fun setup(@Autowired nodeRepo: NodeRepository) {
      val nodes = mutableListOf<Waypoint>()
      for (i in 0..100) {
        nodes.add(Waypoint().apply {
          x = i % 5L
          y = i % 3L
        })
      }
      for (i in 0..100) {
        val c1 = nodes[i]
        val c2 = nodes[(i + 5) % 100]
        c1.connections.add(Connection(c1, c2, 1.0))
      }

      nodeRepo.saveAll(nodes)

      w1 = nodes[10].id
      w2 = nodes[20].id
    }

    @AfterAll
    @JvmStatic
    fun teardown(@Autowired nodeRepo: NodeRepository) {
      nodeRepo.deleteAll()
    }
  }
}