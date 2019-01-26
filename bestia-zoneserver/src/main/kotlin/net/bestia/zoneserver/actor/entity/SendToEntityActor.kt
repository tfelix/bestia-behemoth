package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.cluster.sharding.ClusterSharding
import mu.KotlinLogging
import net.bestia.zoneserver.EntryActorNames
import net.bestia.zoneserver.actor.Actor

private val LOG = KotlinLogging.logger { }

/**
 * This actor sends the incoming message towards the registered cluster sharding
 * actor which manages an entity.
 *
 * @author Thomas Felix
 */
@Actor
class SendToEntityActor : AbstractActor() {

  private var entityActorShard: ActorRef = ClusterSharding.get(context.system)
          .shardRegion(EntryActorNames.SHARD_ENTITY)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(EntityEnvelope::class.java) { msg ->
              LOG.debug("Sending to entity: {}", msg)
              entityActorShard.tell(msg, sender)
            }
            .build()
  }

  companion object {
    const val NAME = "sendToEntity"
  }
}