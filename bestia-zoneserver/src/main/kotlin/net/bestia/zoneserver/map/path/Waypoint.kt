package net.bestia.zoneserver.map.path

import org.neo4j.ogm.annotation.*

@NodeEntity
class Waypoint {
  // neo4j requires the id to be nullable
  // otherwise it does not save
  @Id
  @GeneratedValue
  val id: Long? = null

  @Property
  var x: Long = 0

  @Property
  var y: Long = 0

  @Relationship(type = "CONNECTION", direction = Relationship.UNDIRECTED)
  val connections: MutableList<Connection> = mutableListOf()
}
