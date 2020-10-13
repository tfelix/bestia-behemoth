package net.bestia.zoneserver.map.path

import org.neo4j.ogm.annotation.*

@RelationshipEntity(type = "CONNECTION")
class Connection {
  // neo4j requires the id to be nullable
  // otherwise it does not save
  @Id
  @GeneratedValue
  var relationshipId: Long? = null

  @StartNode
  var start: Waypoint? = null

  @EndNode
  var end: Waypoint? = null

  var weight: Double = 1.0
}