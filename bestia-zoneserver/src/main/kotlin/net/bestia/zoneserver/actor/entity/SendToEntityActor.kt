package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.cluster.sharding.ClusterSharding
import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.ShardActorNames
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

  private val entityActorShard: ActorRef = ClusterSharding.get(context.system)
      .shardRegion(ShardActorNames.SHARD_ENTITY)

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(EntityMessage::class.java) { msg ->
          LOG.debug("Sending to entity: {}", msg)
          entityActorShard.tell(msg, sender)
        }
        .build()
  }

  companion object {
    const val NAME = "sendToEntity"
  }
}