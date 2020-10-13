package net.bestia.zoneserver.map.path

import org.neo4j.ogm.annotation.*

@NodeEntity(label = "Waypoint")
class Waypoint {
  @Id
  @GeneratedValue
  val id: Long = 0

  @Property
  var x: Long = 0

  @Property
  var y: Long = 0

  @Relationship(type = "CONNECTION", direction = Relationship.UNDIRECTED)
  val connections: MutableList<Connection> = mutableListOf()
}
