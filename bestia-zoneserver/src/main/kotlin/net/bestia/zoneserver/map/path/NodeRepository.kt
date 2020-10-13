package net.bestia.zoneserver.map.path

import org.springframework.data.neo4j.repository.Neo4jRepository
import org.springframework.stereotype.Repository

@Repository
interface NodeRepository : Neo4jRepository<Waypoint, Long>