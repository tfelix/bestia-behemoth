package net.bestia.zoneserver.actor.map.quadtree

import akka.actor.AbstractActor
import akka.actor.Props
import akka.actor.ReceiveTimeout
import net.bestia.model.geometry.Shape
import java.time.Duration

data class QuadtreeEntryResponse(
    val entityIds: Set<Long> = emptySet(),
    val areaToCheck: Shape,
    val expectedReplies: Int
)

// Not sure if we should work with callback here because of danger of
// enclosing another actor state within this actor (BAD!) better maybe
// use some reply message.
class AwaitQuadTreeResponseActor(
    private val callback: EntityIdCallback
) : AbstractActor() {

  init {
    context.setReceiveTimeout(Duration.ofMillis(REPLY_TIMEOUT_MS))
  }

  private var awaitedResponses = 0
  private var receivedResponses = 0
  private val entityIdsCollected = mutableSetOf<Long>()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(QuadtreeEntryResponse::class.java, this::receiveResponse)
        .match(ReceiveTimeout::class.java) { receiveTimeout() }
        .build()
  }

  private fun receiveTimeout() {
    callback(emptySet())
    context.stop(self)
  }

  private fun receiveResponse(response: QuadtreeEntryResponse) {
    awaitedResponses = response.expectedReplies
    receivedResponses++

    entityIdsCollected.addAll(response.entityIds)

    if (receivedResponses >= awaitedResponses) {
      // We collected all entity ids.
      callback(entityIdsCollected)
      context.stop(self)
    }
  }

  companion object {
    private const val REPLY_TIMEOUT_MS = 1000L

    fun props(callback: EntityIdCallback): Props {
      return Props.create(AwaitQuadTreeResponseActor::class.java) {
        AwaitQuadTreeResponseActor(callback)
      }
    }
  }
}