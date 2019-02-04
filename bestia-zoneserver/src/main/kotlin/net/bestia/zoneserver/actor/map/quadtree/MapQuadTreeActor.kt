package net.bestia.zoneserver.actor.map.quadtree

import akka.actor.ActorRef
import akka.cluster.sharding.ClusterSharding
import akka.persistence.AbstractPersistentActor
import akka.persistence.SnapshotOffer
import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Shape
import net.bestia.zoneserver.ShardActorNames
import net.bestia.zoneserver.actor.Actor
import java.nio.ByteBuffer
import java.util.*

// How to test this actor
// https://tudorzgureanu.com/akka-persistence-testing-persistent-actors/

typealias NodeAddress = String

data class InitCommand(
    val boundary: Rect
)

data class AddCommand(
    val nodeAdress: NodeAddress,
    val entityShape: Shape,
    val entityId: Long
)

data class RemoveCommand(
    val nodeAdress: NodeAddress,
    val entityId: Long
)

data class QueryCommand(
    val nodeAdress: NodeAddress,
    val expectedReplies: Int = 0,
    val queryArea: Shape,
    val replyTo: ActorRef
)

@Actor
class MapQuadTreeActor : AbstractPersistentActor() {

  private data class QTreeState(
      val entities: MutableList<AddCommand> = mutableListOf(),
      val childs: MutableList<Rect> = mutableListOf()
  ) {
    val hasChilds get() = childs.isNotEmpty()
  }

  private lateinit var boundary: Rect
  private var state = QTreeState()
  private val qtreeShardRegion = ClusterSharding.get(context.system)
      .shardRegion(ShardActorNames.SHARD_QUADTREE)

  override fun persistenceId(): String {
    val bytes = ByteBuffer.allocate(4).putInt(boundary.hashCode()).array()

    return UUID.nameUUIDFromBytes(bytes).toString()
  }

  override fun createReceiveRecover(): Receive {
    return receiveBuilder()
        .match(AddCommand::class.java) { addEntry(it) }
        .match(SnapshotOffer::class.java) { state = it.snapshot() as QTreeState }
        .build()
  }

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(InitCommand::class.java, this::init)
        .match(AddCommand::class.java, this::addEntry)
        .match(QueryCommand::class.java, this::query)
        .build()
  }

  private fun init(cmd: InitCommand) {
    persist(cmd) {
      boundary = it.boundary
    }
  }

  private fun addEntry(entry: AddCommand) {
    if (!boundary.collide(entry.entityShape)) {
      return
    }

    persist(entry) {
      if (state.entities.size < MAX_ENTRIES) {
        state.entities.add(entry)
        return@persist
      }

      createChilds()
      state.childs
          .filter { child -> child.collide(it.entityShape) }
          .forEach {

          }
    }
  }

  private fun createChilds() {
    if (state.hasChilds) {
      return
    }

    val hHeight = boundary.height / 2
    val hWidth = boundary.width / 2
    val nw = Rect(boundary.x, boundary.y, hHeight, hWidth)
    val ne = Rect(boundary.x + hWidth, boundary.y, hWidth, hHeight)
    val sw = Rect(boundary.x, boundary.y + hHeight, hHeight, hWidth)
    val se = Rect(boundary.x + hWidth, boundary.y + hHeight, hHeight, hWidth)

    this.state.childs.addAll(listOf(nw, ne, sw, se))
  }

  private fun query(query: QueryCommand) {
    if (!state.hasChilds) {
      val collidingEntityIds = state.entities
          .filter { it.entityShape.collide(query.queryArea) }
          .map { it.entityId }
          .toSet()
      query.replyTo.tell(QuadtreeEntryResponse(
          entityIds = collidingEntityIds,
          areaToCheck = query.queryArea,
          expectedReplies = query.expectedReplies
      ), self)

      return
    }

    val collidingChilds = state.childs.filter { it.collide(query.queryArea) }
    val addedResponseCount = collidingChilds.size
    collidingChilds.forEach {
          val cmdMsg = QueryCommand(
              expectedReplies = query.expectedReplies + addedResponseCount,
              nodeAdress = rectToNodeHash(it),
              queryArea = query.queryArea,
              replyTo = query.replyTo
          )
      qtreeShardRegion.tell(cmdMsg, self)
    }
  }

  companion object {
    private const val MAX_ENTRIES = 5

    fun rectToNodeHash(rect: Rect): NodeAddress {
      return rect.toString()
    }
  }
}