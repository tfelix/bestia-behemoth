package net.bestia.zoneserver.map.navigation

import org.neo4j.ogm.annotation.*

@NodeEntity
data class Node(
    @Id
    @GeneratedValue
    val id: Long,

    @Index
    val x: Long,

    @Index
    val y: Long,

    @Relationship(type = "TEAMMATE", direction = Relationship.UNDIRECTED)
    val teammates: MutableSet<Node>
)