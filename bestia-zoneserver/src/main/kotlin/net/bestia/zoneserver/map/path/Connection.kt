package net.bestia.zoneserver.map.path

import org.neo4j.ogm.annotation.*

@RelationshipEntity(type = "CONNECTION")
class Connection(
    @StartNode
    private var start: Waypoint,

    @EndNode
    private var end: Waypoint,

    private var weight: Double
) {
  @Id
  @GeneratedValue
  private var relationshipId: Long? = null
}