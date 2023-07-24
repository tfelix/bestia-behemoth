package net.bestia.zoneserver.script

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import net.bestia.zoneserver.entity.Entity

class EntityQueryActor private constructor(
    context: ActorContext<Query>
) : AbstractBehavior<EntityQueryActor.Query>(context) {

    sealed interface Query

    data class EntityQuery(
        val entityId: Long,
        val replyTo: ActorRef<QueriedEntityEvent>
    ) : Query

    sealed interface Event

    data class QueriedEntityEvent(
        val entity: Entity?
    ) : Event

    private fun onQueryEntity(entityQuery: EntityQuery): Behavior<Query> {
        // TODO Find Entity

        entityQuery.replyTo.tell(QueriedEntityEvent(entity = null))

        return this
    }

    override fun createReceive(): Receive<Query> {
        return newReceiveBuilder().onMessage(EntityQuery::class.java, ::onQueryEntity).build()
    }

    companion object {
        fun create(): Behavior<Query> {
            return Behaviors.setup { context: ActorContext<Query> -> EntityQueryActor(context) }
        }
    }
}

