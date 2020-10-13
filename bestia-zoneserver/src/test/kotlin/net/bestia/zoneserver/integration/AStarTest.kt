package net.bestia.zoneserver.integration

import net.bestia.zoneserver.map.path.Connection
import net.bestia.zoneserver.map.path.NodeRepository
import net.bestia.zoneserver.map.path.Waypoint
import org.junit.Assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.neo4j.ogm.session.SessionFactory
import org.springframework.beans.factory.annotation.Autowired
import kotlin.math.pow
import kotlin.math.sqrt

@IntegrationTest
class AStarTest {

  @Autowired
  private lateinit var nodeRepository: NodeRepository

  @Test
  fun anotherTest() {
    println(nodeRepository.findAll())

    val nodes = mutableListOf<Waypoint>()
    for (i in 0..100) {
      nodes.add(Waypoint().apply {
        x = i % 5L
        y = i % 3L
      })
    }

    nodeRepository.saveAll(nodes)
    println(nodeRepository.findAll())
  }

  // @Test
  /*
  fun test() {
    val result = session.query(Waypoint::class.java, """
      MATCH (start:Waypoint {id: $w1}), (end:Waypoint {id: $w2})
      CALL gds.alpha.shortestPath.astar.stream({
        nodeQuery: 'MATCH (p:Waypoint) RETURN id(p) AS id, p.x AS x, p.y AS y',
        relationshipQuery: 'MATCH (p1:Waypoint)-[r:CONNECTION]->(p2:Waypoint) RETURN id(p1) AS source, id(p2) AS target, r.weight AS weight',
        startNode: start,
        endNode: end,
        relationshipWeightProperty: 'weight',
        propertyKeyLat: 'x',
        propertyKeyLat: 'y'
      })
      YIELD nodeId, cost
      RETURN gds.util.asNode(nodeId).id AS id, cost
    """.trimIndent(), emptyMap<String, Any>())

    println(result)
  }*/

  companion object {
    private var w1 = 0L
    private var w2 = 0L
  }
}