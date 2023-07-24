package net.bestia.zoneserver.script

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

class ScriptMessageProcessorActor private constructor(
    context: ActorContext<Command>,
    private val entityQuery: ActorRef<EntityQueryActor.Query>
) : AbstractBehavior<ScriptMessageProcessorActor.Command>(context) {

    sealed interface Command

    data class ProcessScriptMessage(
        val scriptMessage: ScriptMessage
    ) : Command

    override fun createReceive(): Receive<Command> {
        return newReceiveBuilder()
            .onMessage(ProcessScriptMessage::class.java, ::onScriptMessage)
            .build()
    }

    private fun onScriptMessage(command: ProcessScriptMessage): Behavior<Command> {
        when (command.scriptMessage) {
            is EntitiesByShapeQuery -> entityQuery.tell(
                EntityQueryActor.EntityQuery(
                    entityId = 1L,
                    replyTo = //
                )
            )
            is EntityByIdQuery -> TODO()
        }

        return this
    }

    companion object {
        fun create(
            entityQuery: ActorRef<EntityQueryActor.Query>
        ): Behavior<Command> {
            return Behaviors.setup { context -> ScriptMessageProcessorActor(context, entityQuery) }
        }
    }
}