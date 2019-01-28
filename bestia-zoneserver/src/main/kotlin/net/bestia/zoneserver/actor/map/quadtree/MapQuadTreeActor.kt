package net.bestia.zoneserver.actor.map.quadtree

import akka.actor.ActorRef
import akka.actor.Props
import akka.cluster.sharding.ClusterSharding
import akka.persistence.AbstractPersistentActor
import akka.persistence.SnapshotOffer
import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Shape
import net.bestia.zoneserver.ShardActorNames
import net.bestia.zoneserver.actor.Actor
import java.nio.ByteBuffer
import java.util.*

data class QuadtreeEntry(
    val collision: Shape,
    val entityId: Long
)

data class QuadtreeEntryResponse(
    val entityIds: List<Long> = emptyList(),
    val areaToCheck: Shape,
    val expectedReplies: Int
)

interface QuadtreeQuery {
  val areaToCheck: Shape
  val replyTo: ActorRef
}

/*
@Actor
class MapQuadTreeActor(
    private val boundary: Rect
) : AbstractPersistentActor() {

  data class QuadtreeSplitQuery(
      areaToCheck: Shape,
      replyTo: ActorRef,
      internal val querySplits: Int = 0
  ) : QuadtreeQuery(areaToCheck, replyTo)

  private val qtreeShardRegion = ClusterSharding.get(context.system).shardRegion(ShardActorNames.SHARD_QUADTREE)

  private data class QTreeState(
      val entities: MutableList<QuadtreeEntry> = mutableListOf(),
      var northWest: Rect? = null,
      var northEast: Rect? = null,
      var southWest: Rect? = null,
      var southEast: Rect? = null
  )

  private var state = QTreeState()

  override fun persistenceId(): String {
    val bytes = ByteBuffer.allocate(4).putInt(boundary.hashCode()).array()

    return UUID.nameUUIDFromBytes(bytes).toString()
  }

  override fun createReceiveRecover(): Receive {
    return receiveBuilder()
        .match(QuadtreeEntry::class.java) { state.update() }
        .match(SnapshotOffer::class.java) { state = it.snapshot() as QTreeState }
        .build()
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(QuadtreeEntry::class.java, this::addEntry)
        .match(QuadtreeEntryQuery::class.java, this::queryEntry)
        .build()
  }

  private fun addEntry(entry: QuadtreeEntry) {
    if (!boundary.collide(entry.collision)) {
      return
    }

    persist(entry) {
      /*
      if (lastSequenceNr() % snapShotInterval == 0 && lastSequenceNr() != 0)
          // IMPORTANT: create a copy of snapshot because ExampleState is mutable
          saveSnapshot(state.copy());
       */

      if (state.entities.size < MAX_ENTRIES) {
        state.entities.add(entry)
        return@persist
      }

      val childs = getChildsOrSubdivide()

      childs.northEast.tell(entry, self)
      childs.northWest.tell(entry, self)
      childs.southEast.tell(entry, self)
      childs.southWest.tell(entry, self)
    }
  }

  private fun getChildsOrSubdivide(): TreeNodes {
    if (childs == null) {
      val hHeight = boundary.height / 2
      val hWidth = boundary.width / 2
      val nw = Rect(boundary.x, boundary.y, hHeight, hWidth)
      val ne = Rect(boundary.x + hWidth, boundary.y, hWidth, hHeight)
      val sw = Rect(boundary.x, boundary.y + hHeight, hHeight, hWidth)
      val se = Rect(boundary.x + hWidth, boundary.y + hHeight, hHeight, hWidth)

      val nwa = context().actorOf(props(nw))
      val nea = context().actorOf(props(ne))
      val swa = context().actorOf(props(sw))
      val sea = context().actorOf(props(se))

      childs = TreeNodes(nwa, nea, swa, sea)
    }

    return childs!!
  }

  private fun queryEntry(query: QuadtreeEntryQuery) {
    if (!boundary.collide(query.areaToCheck)) {
      query.replyTo.tell(QuadtreeEntryResponse(
          areaToCheck = query.areaToCheck
      ), self)

      return
    }

    if (childs == null) {
      val collidingEntityIds = entities
          .filter { it.collision.collide(query.areaToCheck) }
          .map { it.entityId }
          .toList()
      query.replyTo.tell(QuadtreeEntryResponse(
          entityIds = collidingEntityIds,
          areaToCheck = query.areaToCheck
      ), self)

      return
    }

    childs!!.southWest.tell(query, self)
    childs!!.southEast.tell(query, self)
    childs!!.northWest.tell(query, self)
    childs!!.northEast.tell(query, self)
  }

  companion object {
    private const val MAX_ENTRIES = 5

    fun props(
        boundary: Rect
    ): Props {
      return Props.create(MapQuadTreeActor::class.java) {
        MapQuadTreeActor(boundary)
      }
    }
  }
}*/