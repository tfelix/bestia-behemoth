package net.bestia.zoneserver.actor.map.quadtree

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.cluster.sharding.ClusterSharding
import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Shape
import net.bestia.model.geometry.Size
import net.bestia.zoneserver.ShardActorNames
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PositionComponent

typealias EntityIdCallback = (entityIds: Set<Long>) -> Unit

class EntityCollisionService(
    actorSystem: ActorSystem,
    private val mapBoundary: Rect
) {
  private val qtreeShard = ClusterSharding.get(actorSystem).shardRegion(ShardActorNames.SHARD_QUADTREE)

  init {
    qtreeShard.tell(InitCommand(mapBoundary), ActorRef.noSender())
  }

  constructor(mapSize: Size, actorSystem: ActorSystem) : this(
      actorSystem,
      Rect(0, 0, mapSize.width, mapSize.height)
  )

  fun addEntity(entity: Entity) {
    val posComp = entity.getComponent(PositionComponent::class.java)
    val entityBoundry = posComp.shape
    val addCommand = AddCommand(
        entityId = entity.id,
        entityShape = entityBoundry,
        nodeAdress = MapQuadTreeActor.rectToNodeHash(mapBoundary)
    )

    qtreeShard.tell(addCommand, ActorRef.noSender())
  }

  fun getEntityCollidingWith(shape: Shape, callback: EntityIdCallback, context: ActorContext) {
    val awaitReplyActor = context.actorOf(AwaitQuadTreeResponseActor.props(callback))
    val query = QueryCommand(
        nodeAdress = MapQuadTreeActor.rectToNodeHash(mapBoundary),
        queryArea = shape,
        replyTo = awaitReplyActor
    )

    qtreeShard.tell(query, ActorRef.noSender())
  }

  fun removeEntity(entityId: Long) {
    val removeCommand = RemoveCommand(
        entityId = entityId,
        nodeAdress = MapQuadTreeActor.rectToNodeHash(mapBoundary)
    )

    qtreeShard.tell(removeCommand, ActorRef.noSender())
  }
}