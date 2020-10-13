package net.bestia.zoneserver.map.path

import org.neo4j.graphdb.Node
import org.springframework.data.jpa.repository.Query
import org.springframework.data.neo4j.repository.Neo4jRepository
import org.springframework.stereotype.Repository

@Repository
interface NodeRepository : Neo4jRepository<Waypoint, Long> {

  @Query("MATCH (n) WHERE id(n)=$0 RETURN n")
  fun findNodeById(id: Long): Node?
}